import { useEffect, useState } from 'react';
import type { Document } from './types';
import { listDocuments } from './api';
import DocumentUpload from './components/DocumentUpload';
import DocumentList from './components/DocumentList';
import DocumentDetail from './components/DocumentDetail';
import './App.css';

export default function App() {
  const [documents, setDocuments] = useState<Document[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  function refresh() {
    listDocuments().then(setDocuments).catch(() => {});
  }

  useEffect(refresh, []);

  function handleUploaded(_doc: Document) {
    refresh();
  }

  if (selectedId) {
    return (
      <DocumentDetail
        documentId={selectedId}
        onBack={() => {
          setSelectedId(null);
          refresh();
        }}
      />
    );
  }

  return (
    <div className="app">
      <header>
        <h1>Forestry AI Reviewer</h1>
        <p className="subtitle">Upload environmental documents for AI-assisted review</p>
      </header>
      <main>
        <DocumentUpload onUploaded={handleUploaded} />
        <DocumentList documents={documents} onSelect={(id) => setSelectedId(id)} />
      </main>
    </div>
  );
}
