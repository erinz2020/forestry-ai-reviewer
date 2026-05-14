import type { Document } from '../types';

interface Props {
  documents: Document[];
  onSelect: (id: string) => void;
}

function statusLabel(status: Document['status']) {
  const map: Record<Document['status'], string> = {
    UPLOADING: 'Uploading',
    EXTRACTING: 'Extracting',
    READY: 'Ready',
    FAILED: 'Failed',
  };
  return map[status];
}

export default function DocumentList({ documents, onSelect }: Props) {
  if (documents.length === 0) {
    return <p className="empty">No documents uploaded yet.</p>;
  }

  return (
    <table className="doc-table">
      <thead>
        <tr>
          <th>File Name</th>
          <th>Status</th>
          <th>Uploaded</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {documents.map((doc) => (
          <tr key={doc.id}>
            <td>{doc.fileName}</td>
            <td>
              <span className={`status-badge status-${doc.status.toLowerCase()}`}>
                {statusLabel(doc.status)}
              </span>
            </td>
            <td>{new Date(doc.uploadedAt).toLocaleString()}</td>
            <td>
              <button className="btn-link" onClick={() => onSelect(doc.id)}>
                View
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
