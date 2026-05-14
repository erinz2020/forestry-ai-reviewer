import { useState, useRef } from 'react';
import type { Document } from '../types';
import { uploadDocument } from '../api';

interface Props {
  onUploaded: (doc: Document) => void;
}

export default function DocumentUpload({ onUploaded }: Props) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  async function handleFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    const ext = file.name.split('.').pop()?.toLowerCase();
    if (ext !== 'pdf' && ext !== 'docx') {
      setError('Only PDF and DOCX files are accepted.');
      return;
    }

    setUploading(true);
    setError(null);
    try {
      const doc = await uploadDocument(file);
      onUploaded(doc);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  }

  return (
    <div className="upload-area">
      <label htmlFor="file-input" className="upload-label">
        {uploading ? 'Uploading and extracting text...' : 'Drop a PDF or DOCX file here, or click to browse'}
      </label>
      <input
        id="file-input"
        ref={inputRef}
        type="file"
        accept=".pdf,.docx"
        onChange={handleFile}
        disabled={uploading}
      />
      {error && <p className="error">{error}</p>}
    </div>
  );
}
