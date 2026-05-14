import type { Finding, FindingStatus } from '../types';
import { updateFindingStatus } from '../api';

interface Props {
  finding: Finding;
  onStatusChange: (finding: Finding) => void;
}

const STATUS_OPTIONS: { value: FindingStatus; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'IGNORED', label: 'Ignored' },
  { value: 'NEEDS_FOLLOW_UP', label: 'Needs Follow-up' },
];

function severityClass(severity: Finding['severity']) {
  return `severity-${severity.toLowerCase()}`;
}

function typeLabel(type: Finding['type']) {
  return type.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

export default function FindingRow({ finding, onStatusChange }: Props) {
  async function handleStatusChange(e: React.ChangeEvent<HTMLSelectElement>) {
    const newStatus = e.target.value as FindingStatus;
    try {
      const updated = await updateFindingStatus(finding.id, newStatus);
      onStatusChange(updated);
    } catch {
      alert('Failed to update status.');
    }
  }

  return (
    <tr>
      <td>
        <span className={`severity-badge ${severityClass(finding.severity)}`}>
          {finding.severity}
        </span>
      </td>
      <td className="finding-type">{typeLabel(finding.type)}</td>
      <td>{finding.location}</td>
      <td className="finding-description">{finding.description}</td>
      <td className="finding-quote">{finding.quote}</td>
      <td className="finding-suggestion">{finding.suggestion}</td>
      <td>
        <select
          value={finding.status}
          onChange={handleStatusChange}
          className={`status-select status-select-${finding.status.toLowerCase()}`}
        >
          {STATUS_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </td>
    </tr>
  );
}
