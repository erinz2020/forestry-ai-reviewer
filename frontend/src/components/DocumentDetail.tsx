import { useEffect, useState } from 'react';
import type { Document as DocType, Finding, FindingStatus } from '../types';
import { getDocument, reviewDocument, getFindings, exportAnnotatedDocument } from '../api';
import FindingRow from './FindingRow';

interface Props {
  documentId: string;
  onBack: () => void;
}

function countByStatus(findings: Finding[], status: FindingStatus) {
  return findings.filter((f) => f.status === status).length;
}

export default function DocumentDetail({ documentId, onBack }: Props) {
  const [doc, setDoc] = useState<DocType | null>(null);
  const [findings, setFindings] = useState<Finding[]>([]);
  const [reviewing, setReviewing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [author, setAuthor] = useState('liujh');
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    getDocument(documentId).then(setDoc).catch(() => setError('Failed to load document'));
    getFindings(documentId).then(setFindings).catch(() => {});
  }, [documentId]);

  async function handleReview() {
    setReviewing(true);
    setError(null);
    try {
      const result = await reviewDocument(documentId);
      setFindings(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Review failed');
    } finally {
      setReviewing(false);
    }
  }

  function handleStatusChange(updated: Finding) {
    setFindings((prev) => prev.map((f) => (f.id === updated.id ? updated : f)));
  }

  async function handleExport() {
    setExporting(true);
    setError(null);
    try {
      await exportAnnotatedDocument(documentId, author.trim() || 'liujh');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Export failed');
    } finally {
      setExporting(false);
    }
  }

  if (error) return <p className="error">{error}</p>;
  if (!doc) return <p>Loading...</p>;

  const confirmed = countByStatus(findings, 'CONFIRMED');
  const ignored = countByStatus(findings, 'IGNORED');
  const followUp = countByStatus(findings, 'NEEDS_FOLLOW_UP');

  return (
    <div className="document-detail">
      <button className="btn-back" onClick={onBack}>
        &larr; Back to documents
      </button>

      <div className="detail-header">
        <div>
          <h2>{doc.fileName}</h2>
          <p className="meta">
            Uploaded {new Date(doc.uploadedAt).toLocaleString()} &middot;{' '}
            <span className={`status-badge status-${doc.status.toLowerCase()}`}>{doc.status}</span>
          </p>
        </div>
        <button
          className="btn-review"
          onClick={handleReview}
          disabled={reviewing || doc.status !== 'READY'}
        >
          {reviewing ? 'Reviewing...' : findings.length > 0 ? 'Re-run Review' : 'Start AI Review'}
        </button>
      </div>

      {findings.length > 0 && (
        <>
          <div className="summary-bar">
            <span className="summary-item">Total: {findings.length}</span>
            <span className="summary-item summary-confirmed">Confirmed: {confirmed}</span>
            <span className="summary-item summary-ignored">Ignored: {ignored}</span>
            <span className="summary-item summary-followup">Follow-up: {followUp}</span>
          </div>

          <div className="export-bar">
            <label>
              <span>Comment author</span>
              <input
                type="text"
                value={author}
                onChange={(e) => setAuthor(e.target.value)}
                placeholder="liujh"
              />
            </label>
            <button
              className="btn-review"
              onClick={handleExport}
              disabled={exporting || confirmed === 0}
              title={confirmed === 0 ? 'No CONFIRMED findings yet' : ''}
            >
              {exporting
                ? 'Exporting...'
                : `Export annotated .docx (${confirmed} CONFIRMED)`}
            </button>
          </div>

          <table className="findings-table">
            <thead>
              <tr>
                <th>Severity</th>
                <th>Type</th>
                <th>Location</th>
                <th>Issue</th>
                <th>Quote</th>
                <th>Suggestion</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {findings.map((f) => (
                <FindingRow key={f.id} finding={f} onStatusChange={handleStatusChange} />
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}
