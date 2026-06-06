import { Fragment, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import type { ReviewCase } from '../types';
import {
  listReviewCases,
  uploadReviewCaseAnnotated,
  uploadReviewCasePair,
  uploadReviewerNotes,
} from '../api';

type UploadMode = 'pair' | 'annotated' | 'notes';

function preview(value: string | null, limit = 120) {
  if (!value) return '—';
  return value.length > limit ? `${value.slice(0, limit - 3)}...` : value;
}

function sourceTypeLabel(sourceType: ReviewCase['sourceType']) {
  const labels: Record<ReviewCase['sourceType'], string> = {
    TEXT_DIFF: 'Text diff',
    REVIEW_COMMENT: 'Review comment',
    BOTH: 'Both',
    TRACKED_REVISION: 'Tracked revision',
    REVIEWER_NOTES: 'Reviewer notes',
  };
  return labels[sourceType];
}

export default function HistoricalReviewCases() {
  const [cases, setCases] = useState<ReviewCase[]>([]);
  const [mode, setMode] = useState<UploadMode>('pair');
  const [beforeFile, setBeforeFile] = useState<File | null>(null);
  const [afterFile, setAfterFile] = useState<File | null>(null);
  const [annotatedFile, setAnnotatedFile] = useState<File | null>(null);
  const [notesFile, setNotesFile] = useState<File | null>(null);
  const [relatedFileName, setRelatedFileName] = useState('');
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

  function resetForm() {
    setBeforeFile(null);
    setAfterFile(null);
    setAnnotatedFile(null);
    setNotesFile(null);
    setRelatedFileName('');
    setTitle('');
    setDocumentType('');
  }

  function switchMode(next: UploadMode) {
    if (next === mode) return;
    setMode(next);
    setError(null);
    setSuccess(null);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSuccess(null);

    if (mode === 'pair' && (!beforeFile || !afterFile)) {
      setError('Select both the original draft and reviewed file.');
      return;
    }
    if (mode === 'annotated' && !annotatedFile) {
      setError('Select the annotated document to upload.');
      return;
    }
    if (mode === 'notes' && !notesFile) {
      setError('Select the reviewer notes document to upload.');
      return;
    }

    setLoading(true);
    try {
      let created;
      if (mode === 'pair') {
        created = await uploadReviewCasePair({
          beforeFile: beforeFile!,
          afterFile: afterFile!,
          title: title.trim() || undefined,
          documentType: documentType.trim() || undefined,
        });
      } else if (mode === 'annotated') {
        created = await uploadReviewCaseAnnotated({
          annotatedFile: annotatedFile!,
          title: title.trim() || undefined,
          documentType: documentType.trim() || undefined,
        });
      } else {
        created = await uploadReviewerNotes({
          notesFile: notesFile!,
          relatedFileName: relatedFileName.trim() || undefined,
          title: title.trim() || undefined,
          documentType: documentType.trim() || undefined,
        });
      }

      setSuccess(`Created ${created.length} review case${created.length === 1 ? '' : 's'}.`);
      resetForm();
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
          <p className="meta">
            Capture expert review examples — either by uploading the original draft alongside the
            reviewed version, or by uploading a single document that contains expert comments.
          </p>
        </div>
      </div>

      <div className="mode-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'pair'}
          className={mode === 'pair' ? 'tab tab-active' : 'tab'}
          onClick={() => switchMode('pair')}
        >
          Upload draft + reviewed pair
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'annotated'}
          className={mode === 'annotated' ? 'tab tab-active' : 'tab'}
          onClick={() => switchMode('annotated')}
        >
          Upload single annotated document
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'notes'}
          className={mode === 'notes' ? 'tab tab-active' : 'tab'}
          onClick={() => switchMode('notes')}
        >
          Upload reviewer notes document
        </button>
      </div>

      <form className="pair-upload" onSubmit={handleSubmit}>
        <div className="form-grid">
          {mode === 'pair' ? (
            <>
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
            </>
          ) : mode === 'annotated' ? (
            <label>
              <span>Annotated document (.docx with comments, or .pdf with annotations)</span>
              <input
                type="file"
                accept=".docx,.pdf,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                onChange={(e) => setAnnotatedFile(e.target.files?.[0] ?? null)}
              />
            </label>
          ) : (
            <>
              <label>
                <span>Reviewer notes document (free-form opinions, .docx / .pdf / .txt)</span>
                <input
                  type="file"
                  accept=".docx,.pdf,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
                  onChange={(e) => setNotesFile(e.target.files?.[0] ?? null)}
                />
              </label>
              <label>
                <span>Related document filename (optional)</span>
                <input
                  type="text"
                  value={relatedFileName}
                  onChange={(e) => setRelatedFileName(e.target.value)}
                  placeholder="e.g. 提交版.docx"
                />
              </label>
            </>
          )}
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
          {loading
            ? 'Extracting...'
            : mode === 'pair'
              ? 'Upload Pair'
              : mode === 'annotated'
                ? 'Upload Annotated Document'
                : 'Upload Reviewer Notes'}
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
              <th>Comment Author</th>
              <th>Location</th>
              <th>Detected Change</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {cases.map((reviewCase) => (
              <Fragment key={reviewCase.id}>
                <tr onClick={() => setExpandedId(expandedId === reviewCase.id ? null : reviewCase.id)}>
                  <td>{reviewCase.title || '—'}</td>
                  <td>{reviewCase.documentType || '—'}</td>
                  <td>
                    <span className={`source-badge source-${reviewCase.sourceType.toLowerCase()}`}>
                      {sourceTypeLabel(reviewCase.sourceType)}
                    </span>
                  </td>
                  <td>{reviewCase.sourceDraftFileName || '—'}</td>
                  <td>{reviewCase.sourceReviewedFileName}</td>
                  <td>{preview(reviewCase.originalText)}</td>
                  <td>{preview(reviewCase.reviewedText)}</td>
                  <td>{preview(reviewCase.reviewerComment)}</td>
                  <td>{reviewCase.commentAuthor || '—'}</td>
                  <td>{reviewCase.commentLocation || '—'}</td>
                  <td>{preview(reviewCase.detectedChange)}</td>
                  <td>{new Date(reviewCase.createdAt).toLocaleString()}</td>
                </tr>
                {expandedId === reviewCase.id && (
                  <tr className="expanded-row">
                    <td colSpan={12}>
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
                          {(reviewCase.commentAuthor || reviewCase.commentLocation) && (
                            <p className="meta">
                              {reviewCase.commentAuthor && <>By {reviewCase.commentAuthor}</>}
                              {reviewCase.commentAuthor && reviewCase.commentLocation && ' · '}
                              {reviewCase.commentLocation && <>{reviewCase.commentLocation}</>}
                            </p>
                          )}
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
