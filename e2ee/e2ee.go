// Package e2ee implements the client-owned cryptographic primitives used by PhotoVault E2EE vaults.
package e2ee

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"errors"
	"fmt"
	"io"

	"golang.org/x/crypto/argon2"
)

const (
	AlgorithmVersion = 1
	KeyBytes         = 32
	NonceBytes       = 12
	DefaultChunkSize = 4 << 20
)

type KDFParams struct {
	Time    uint32
	Memory  uint32
	Threads uint8
}

func DefaultKDFParams() KDFParams { return KDFParams{Time: 3, Memory: 64 * 1024, Threads: 4} }
func DeriveMasterKey(passphrase string, salt []byte, params KDFParams) ([]byte, error) {
	if len(salt) < 16 {
		return nil, errors.New("salt must be at least 16 bytes")
	}
	if params.Time == 0 || params.Memory == 0 || params.Threads == 0 {
		return nil, errors.New("invalid Argon2id parameters")
	}
	return argon2.IDKey([]byte(passphrase), salt, params.Time, params.Memory, params.Threads, KeyBytes), nil
}
func RandomKey() ([]byte, error) {
	b := make([]byte, KeyBytes)
	_, err := io.ReadFull(rand.Reader, b)
	return b, err
}
func RandomNonce() ([]byte, error) {
	b := make([]byte, NonceBytes)
	_, err := io.ReadFull(rand.Reader, b)
	return b, err
}
func Zero(key []byte) {
	for i := range key {
		key[i] = 0
	}
}

// WrapFileKey encrypts a per-file key under the unlocked client master key. The result is nonce || ciphertext.
func WrapFileKey(master, fileKey []byte) ([]byte, error) {
	nonce, err := RandomNonce()
	if err != nil {
		return nil, err
	}
	sealed, err := seal(master, nonce, fileKey, nil)
	if err != nil {
		return nil, err
	}
	return append(nonce, sealed...), nil
}
func UnwrapFileKey(master, wrapped []byte) ([]byte, error) {
	if len(wrapped) < NonceBytes {
		return nil, errors.New("wrapped key is too short")
	}
	return open(master, wrapped[:NonceBytes], wrapped[NonceBytes:], nil)
}

// EncryptChunk authenticates one bounded plaintext chunk. Chunk nonces are unique for a file for up to 2^32 chunks.
func EncryptChunk(fileKey, baseNonce []byte, index uint32, plaintext []byte) ([]byte, error) {
	return seal(fileKey, chunkNonce(baseNonce, index), plaintext, chunkAAD(index))
}
func DecryptChunk(fileKey, baseNonce []byte, index uint32, ciphertext []byte) ([]byte, error) {
	return open(fileKey, chunkNonce(baseNonce, index), ciphertext, chunkAAD(index))
}
func chunkNonce(base []byte, index uint32) []byte {
	nonce := make([]byte, NonceBytes)
	copy(nonce, base)
	binary.BigEndian.PutUint32(nonce[8:], index)
	return nonce
}
func chunkAAD(index uint32) []byte {
	aad := make([]byte, 4)
	binary.BigEndian.PutUint32(aad, index)
	return aad
}
func seal(key, nonce, plaintext, aad []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	return aead.Seal(nil, nonce, plaintext, aad), nil
}
func open(key, nonce, ciphertext, aad []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	plain, err := aead.Open(nil, nonce, ciphertext, aad)
	if err != nil {
		return nil, fmt.Errorf("authenticate ciphertext: %w", err)
	}
	return plain, nil
}

// Encrypt streams fixed-size independently authenticated chunks without buffering the complete file.
func Encrypt(source io.Reader, destination io.Writer, fileKey, baseNonce []byte, chunkSize int) (int64, int, error) {
	if chunkSize <= 0 {
		chunkSize = DefaultChunkSize
	}
	buffer := make([]byte, chunkSize)
	var total int64
	index := uint32(0)
	for {
		n, err := source.Read(buffer)
		if n > 0 {
			sealed, sealErr := EncryptChunk(fileKey, baseNonce, index, buffer[:n])
			if sealErr != nil {
				return total, int(index), sealErr
			}
			if _, writeErr := destination.Write(sealed); writeErr != nil {
				return total, int(index), writeErr
			}
			total += int64(n)
			index++
		}
		if err == io.EOF {
			return total, int(index), nil
		}
		if err != nil {
			return total, int(index), err
		}
	}
}
func CiphertextHash(source io.Reader) (string, error) {
	h := sha256.New()
	if _, err := io.Copy(h, source); err != nil {
		return "", err
	}
	return fmt.Sprintf("%x", h.Sum(nil)), nil
}
