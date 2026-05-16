import type { Document, Finding, FindingStatus, ReviewCase } from './types';

const BASE = '/api';

export async function uploadDocument(file: File): Promise<Document> {
  const form = new FormData();
  form.append('file', file);
  const res = await fetch(`${BASE}/documents/upload`, { method: 'POST', body: form });
  if (!res.ok) throw new Error(`Upload failed: ${res.status}`);
  return res.json();
}

export async function listDocuments(): Promise<Document[]> {
  const res = await fetch(`${BASE}/documents`);
  if (!res.ok) throw new Error(`List failed: ${res.status}`);
  return res.json();
}

export async function getDocument(id: string): Promise<Document> {
  const res = await fetch(`${BASE}/documents/${id}`);
  if (!res.ok) throw new Error(`Get failed: ${res.status}`);
  return res.json();
}

export async function reviewDocument(id: string): Promise<Finding[]> {
  const res = await fetch(`${BASE}/documents/${id}/review`, { method: 'POST' });
  if (!res.ok) throw new Error(`Review failed: ${res.status}`);
  return res.json();
}

export async function getFindings(documentId: string): Promise<Finding[]> {
  const res = await fetch(`${BASE}/documents/${documentId}/findings`);
  if (!res.ok) throw new Error(`Findings failed: ${res.status}`);
  return res.json();
}

export async function updateFindingStatus(findingId: string, status: FindingStatus): Promise<Finding> {
  const res = await fetch(`${BASE}/findings/${findingId}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  });
  if (!res.ok) throw new Error(`Status update failed: ${res.status}`);
  return res.json();
}

export async function uploadReviewCasePair(params: {
  beforeFile: File;
  afterFile: File;
  title?: string;
  documentType?: string;
}): Promise<ReviewCase[]> {
  const form = new FormData();
  form.append('beforeFile', params.beforeFile);
  form.append('afterFile', params.afterFile);
  if (params.title) form.append('title', params.title);
  if (params.documentType) form.append('documentType', params.documentType);

  const res = await fetch(`${BASE}/review-cases/upload-pair`, { method: 'POST', body: form });
  if (!res.ok) throw new Error(`Historical review pair upload failed: ${res.status}`);
  return res.json();
}

export async function listReviewCases(): Promise<ReviewCase[]> {
  const res = await fetch(`${BASE}/review-cases`);
  if (!res.ok) throw new Error(`Review cases list failed: ${res.status}`);
  return res.json();
}

export async function getReviewCase(id: string): Promise<ReviewCase> {
  const res = await fetch(`${BASE}/review-cases/${id}`);
  if (!res.ok) throw new Error(`Review case get failed: ${res.status}`);
  return res.json();
}
