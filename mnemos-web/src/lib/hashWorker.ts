/**
 * Off-main-thread SHA-256 hashing via an inline Web Worker.
 *
 * Why: file.arrayBuffer() + crypto.subtle.digest blocks the JS thread
 * for large files (videos). Running it in a Worker keeps the upload
 * queue UI and gallery rendering responsive.
 *
 * Implementation: worker code is embedded as a string and instantiated
 * via a Blob URL — no separate worker file needed, no bundler plugins.
 */

// ─── Worker source (runs in worker thread) ────────────────────────────────────

const WORKER_SRC = /* javascript */ `
function sha256Fallback(bytes) {
  function rightRotate(value, amount) {
    return (value >>> amount) | (value << (32 - amount));
  }
  var mathPow = Math.pow;
  var maxWord = mathPow(2, 32);
  var lengthProperty = 'length';
  var i, j;
  var result = '';

  var words = [];
  var asciiBitLength = bytes[lengthProperty] * 8;
  
  var hash = [];
  var k = [];
  var primeCounter = 0;

  var isComposite = {};
  for (var candidate = 2; primeCounter < 64; candidate++) {
    if (!isComposite[candidate]) {
      for (i = 0; i < 313; i += candidate) {
        isComposite[i] = candidate;
      }
      hash[primeCounter] = (mathPow(candidate, .5) * maxWord) | 0;
      k[primeCounter++] = (mathPow(candidate, 1/3) * maxWord) | 0;
    }
  }
  
  hash = hash.slice(0, 8);
  for (i = 0; i < bytes[lengthProperty]; i++) {
    words[i >> 2] |= bytes[i] << (24 - (i % 4) * 8);
  }
  words[asciiBitLength >> 5] |= 0x80 << (24 - (asciiBitLength % 32));
  words[(((asciiBitLength + 64) >> 9) << 4) + 15] = asciiBitLength;
  
  for (i = 0; i < words[lengthProperty]; i += 16) {
    var w = words.slice(i, i + 16);
    var oldHash = hash.slice(0);
    for (j = 0; j < 64; j++) {
      if (j >= 16) {
        var s0 = rightRotate(w[j - 15], 7) ^ rightRotate(w[j - 15], 18) ^ (w[j - 15] >>> 3);
        var s1 = rightRotate(w[j - 2], 17) ^ rightRotate(w[j - 2], 19) ^ (w[j - 2] >>> 10);
        w[j] = (w[j - 16] + s0 + w[j - 7] + s1) | 0;
      }
      var s1_maj = rightRotate(hash[0], 2) ^ rightRotate(hash[0], 13) ^ rightRotate(hash[0], 22);
      var maj = (hash[0] & hash[1]) ^ (hash[0] & hash[2]) ^ (hash[1] & hash[2]);
      var t2 = (s1_maj + maj) | 0;
      var s0_ch = rightRotate(hash[4], 6) ^ rightRotate(hash[4], 11) ^ rightRotate(hash[4], 25);
      var ch = (hash[4] & hash[5]) ^ ((~hash[4]) & hash[6]);
      var t1 = (hash[7] + s0_ch + ch + k[j] + w[j]) | 0;
      
      hash[7] = hash[6];
      hash[6] = hash[5];
      hash[5] = hash[4];
      hash[4] = (hash[3] + t1) | 0;
      hash[3] = hash[2];
      hash[2] = hash[1];
      hash[1] = hash[0];
      hash[0] = (t1 + t2) | 0;
    }
    for (j = 0; j < 8; j++) {
      hash[j] = (hash[j] + oldHash[j]) | 0;
    }
  }
  
  for (i = 0; i < 8; i++) {
    for (j = 3; j >= 0; j--) {
      var b = (hash[i] >> (8 * j)) & 255;
      result += (b < 16 ? '0' : '') + b.toString(16);
    }
  }
  return result;
}

self.onmessage = async function(e) {
  const { id, file } = e.data;
  try {
    const buffer = await file.arrayBuffer();
    if (typeof crypto !== 'undefined' && crypto.subtle && typeof crypto.subtle.digest === 'function') {
      const digest = await crypto.subtle.digest('SHA-256', buffer);
      const hex = Array.from(new Uint8Array(digest))
        .map(function(b) { return b.toString(16).padStart(2, '0'); })
        .join('');
      self.postMessage({ id, hash: hex });
    } else {
      const hex = sha256Fallback(new Uint8Array(buffer));
      self.postMessage({ id, hash: hex });
    }
  } catch (err) {
    self.postMessage({ id, error: err instanceof Error ? err.message : String(err) });
  }
};
`

// ─── Singleton worker ─────────────────────────────────────────────────────────

let _worker: Worker | null = null
let _workerBlobURL: string | null = null

function getWorker(): Worker {
  if (!_worker) {
    const blob = new Blob([WORKER_SRC], { type: 'text/javascript' })
    _workerBlobURL = URL.createObjectURL(blob)
    _worker = new Worker(_workerBlobURL)

    // If the worker crashes, tear it down so the next call recreates it
    _worker.onerror = () => {
      _worker = null
      if (_workerBlobURL) {
        URL.revokeObjectURL(_workerBlobURL)
        _workerBlobURL = null
      }
    }
  }
  return _worker
}

// ─── Public API ───────────────────────────────────────────────────────────────

let _idCounter = 0

/**
 * Hash a File using SHA-256 in a Web Worker.
 * Non-blocking — the main thread stays responsive during hashing.
 */
export function hashFileInWorker(file: File): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const id = String(++_idCounter)

    const worker = getWorker()

    const onMessage = (e: MessageEvent<{ id: string; hash?: string; error?: string }>) => {
      if (e.data.id !== id) return
      worker.removeEventListener('message', onMessage)
      if (e.data.error) {
        reject(new Error(e.data.error))
      } else {
        resolve(e.data.hash!)
      }
    }

    worker.addEventListener('message', onMessage)
    worker.postMessage({ id, file })
  })
}

/** Tear down the worker (called on logout / page unload) */
export function destroyHashWorker(): void {
  if (_worker) {
    _worker.terminate()
    _worker = null
  }
  if (_workerBlobURL) {
    URL.revokeObjectURL(_workerBlobURL)
    _workerBlobURL = null
  }
}
