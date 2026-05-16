import { Fragment, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import type { ReviewCase } from '../types';
import { listReviewCases, uploadReviewCasePair } from '../api';

function preview(value: string | null, limit = 120) {
  if (!value) return '—';
  return value.length > limit ? `${value.slice(0, limit - 3)}...` : value;
}

function sourceTypeLabel(sourceType: ReviewCase['sourceType']) {
  const labels: Record<ReviewCase['sourceType'], string> = {
    TEXT_DIFF: 'Text diff',
    REVIEW_COMMENT: 'Review comment',
    BOTH: 'Both',
  };
  return labels[sourceType];
}

export default function HistoricalReviewCases() {
  const [cases, setCases] = useState<ReviewCase[]>([]);
  const [beforeFile, setBeforeFile] = useState<File | null>(null);
  const [afterFile, setAfterFile] = useState<File | null>(null);
  const [title, setTitle] = useState('');
  const [documentType, setDocumentType] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  function refresh() {
    listReviewCases().then(setCases).catch(() => setError('Failed to load review cases'));
  }

  useEffect(refresh, []);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!beforeFile || !afterFile) {
      setError('Select both the original draft and reviewed file.');
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const created = await uploadReviewCasePair({
        beforeFile,
        afterFile,
        title: title.trim() || undefined,
        documentType: documentType.trim() || undefined,
      });
      setSuccess(`Created ${created.length} review case${created.length === 1 ? '' : 's'}.`);
      setBeforeFile(null);
      setAfterFile(null);
      setTitle('');
      setDocumentType('');
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="history-page">
      <div className="section-header">
        <div>
          <h2>Historical Review Cases</h2>
          <p className="meta">Upload draft and reviewed versions to extract deterministic review examples.</p>
        </div>
      </div>

      <form className="pair-upload" onSubmit={handleSubmit}>
        <div className="form-grid">
          <label>
            <span>Original draft</span>
            <input
              type="file"
              onChange={(e) => setBeforeFile(e.target.files?.[0] ?? null)}
            />
          </label>
          <label>
            <span>Reviewed version</span>
            <input
              type="file"
              onChange={(e) => setAfterFile(e.target.files?.[0] ?? null)}
            />
          </label>
          <label>
            <span>Title</span>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Optional"
            />
          </label>
          <label>
            <span>Document type</span>
            <input
              type="text"
              value={documentType}
              onChange={(e) => setDocumentType(e.target.value)}
              placeholder="Optional"
            />
          </label>
        </div>
        <button className="btn-review" type="submit" disabled={loading}>
          {loading ? 'Extracting...' : 'Upload Pair'}
        </button>
        {error && <p className="error">{error}</p>}
        {success && <p className="success">{success}</p>}
      </form>

      {cases.length === 0 ? (
        <p className="empty">No historical review cases yet.</p>
      ) : (
        <table className="review-cases-table">
          <thead>
            <tr>
              <th>Title</th>
              <th>Document Type</th>
              <th>Source</th>
              <th>Draft File</th>
              <th>Reviewed File</th>
              <th>Original Text</th>
              <th>Reviewed Text</th>
              <th>Reviewer Comment</th>
              <th>Detected Change</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {cases.map((reviewCase) => (
              <Fragment key={reviewCase.id}>
                <tr key={reviewCase.id} onClick={() => setExpandedId(expandedId === reviewCase.id ? null : reviewCase.id)}>
                  <td>{reviewCase.title || '—'}</td>
                  <td>{reviewCase.documentType || '—'}</td>
                  <td>
                    <span className={`source-badge source-${reviewCase.sourceType.toLowerCase()}`}>
                      {sourceTypeLabel(reviewCase.sourceType)}
                    </span>
                  </td>
                  <td>{reviewCase.sourceDraftFileName}</td>
                  <td>{reviewCase.sourceReviewedFileName}</td>
                  <td>{preview(reviewCase.originalText)}</td>
                  <td>{preview(reviewCase.reviewedText)}</td>
                  <td>{preview(reviewCase.reviewerComment)}</td>
                  <td>{preview(reviewCase.detectedChange)}</td>
                  <td>{new Date(reviewCase.createdAt).toLocaleString()}</td>
                </tr>
                {expandedId === reviewCase.id && (
                  <tr className="expanded-row">
                    <td colSpan={10}>
                      <div className="review-case-detail">
                        <div>
                          <h3>Original</h3>
                          <p>{reviewCase.originalText || '—'}</p>
                        </div>
                        <div>
                          <h3>Reviewed</h3>
                          <p>{reviewCase.reviewedText || '—'}</p>
                        </div>
                        <div>
                          <h3>Comment</h3>
                          <p>{reviewCase.reviewerComment || '—'}</p>
                        </div>
                        <div>
                          <h3>Change</h3>
                          <p>{reviewCase.detectedChange || '—'}</p>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
