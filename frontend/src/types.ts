export interface Document {
  id: string;
  fileName: string;
  contentType: string;
  status: 'UPLOADING' | 'EXTRACTING' | 'READY' | 'FAILED';
  extractedText: string | null;
  uploadedAt: string;
}

export type FindingType =
  | 'INTERNAL_CONTRADICTION'
  | 'MISSING_EVIDENCE'
  | 'BIODIVERSITY_RISK'
  | 'UNSUPPORTED_CONCLUSION'
  | 'VAGUE_LANGUAGE';

export type FindingSeverity = 'HIGH' | 'MEDIUM' | 'LOW';

export type FindingStatus = 'PENDING' | 'CONFIRMED' | 'IGNORED' | 'NEEDS_FOLLOW_UP';

export interface Finding {
  id: string;
  documentId: string;
  type: FindingType;
  severity: FindingSeverity;
  location: string | null;
  quote: string | null;
  description: string;
  suggestion: string | null;
  evidence: string | null;
  status: FindingStatus;
  createdAt: string;
}

export type ReviewCaseSourceType = 'TEXT_DIFF' | 'REVIEW_COMMENT' | 'BOTH' | 'TRACKED_REVISION';

export interface ReviewCase {
  id: string;
  title: string | null;
  documentType: string | null;
  sectionType: string | null;
  originalText: string | null;
  reviewedText: string | null;
  reviewerComment: string | null;
  detectedChange: string | null;
  sourceType: ReviewCaseSourceType;
  sourceDraftFileName: string | null;
  sourceReviewedFileName: string;
  draftChunkIndex: number | null;
  reviewedChunkIndex: number | null;
  commentAuthor: string | null;
  commentLocation: string | null;
  createdAt: string;
}
