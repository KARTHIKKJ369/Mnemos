package e2ee

import (
	"bytes"
	"testing"
)

func TestChunkRoundTripAndTamperDetection(t *testing.T) {
	key := bytes.Repeat([]byte{1}, KeyBytes)
	nonce := bytes.Repeat([]byte{2}, NonceBytes)
	ciphertext, err := EncryptChunk(key, nonce, 4, []byte("private photo"))
	if err != nil {
		t.Fatal(err)
	}
	plain, err := DecryptChunk(key, nonce, 4, ciphertext)
	if err != nil || string(plain) != "private photo" {
		t.Fatalf("plain=%q err=%v", plain, err)
	}
	ciphertext[0] ^= 1
	if _, err := DecryptChunk(key, nonce, 4, ciphertext); err == nil {
		t.Fatal("tampering was accepted")
	}
}
func TestWrappedFileKeyRejectsWrongMaster(t *testing.T) {
	master := bytes.Repeat([]byte{1}, KeyBytes)
	wrong := bytes.Repeat([]byte{2}, KeyBytes)
	file := bytes.Repeat([]byte{3}, KeyBytes)
	wrapped, err := WrapFileKey(master, file)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := UnwrapFileKey(wrong, wrapped); err == nil {
		t.Fatal("wrong password-derived key was accepted")
	}
}
