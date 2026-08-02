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
self.onmessage = async function(e) {
  const { id, file } = e.data;
  try {
    const buffer = await file.arrayBuffer();
    const digest = await crypto.subtle.digest('SHA-256', buffer);
    const hex = Array.from(new Uint8Array(digest))
      .map(function(b) { return b.toString(16).padStart(2, '0'); })
      .join('');
    self.postMessage({ id, hash: hex });
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
