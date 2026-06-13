import React, { useEffect, useMemo, useState } from 'react';

type NodeType = 'root' | 'capability' | 'activity' | 'folder' | 'use-case';

type DocNode = {
  id: string;
  name: string;
  type: NodeType;
  relativePath: string;
  repositoryName: string;
  repositoryUrl: string;
  children: DocNode[];
  useCase: UseCaseDetails | null;
};

type UseCaseDetails = {
  repositoryName: string;
  repositoryUrl: string;
  capabilityId: string;
  activityPath: string;
  activityIds: string[];
  useCaseId: string;
  useCasePath: string;
  relativePath: string;
  ucMarkdown: string;
  featureText: string;
  plantUmlText: string;
  scenarios: ScenarioBlock[];
};

type ScenarioBlock = {
  id: string;
  name: string;
  text: string;
};

type RepositorySummary = {
  name: string;
  url: string;
  checkoutPath: string;
  source: string;
};

type CapabilityTreeResponse = {
  root: DocNode;
  useCases: UseCaseDetails[];
  repositories: RepositorySummary[];
};

export default function CapabilitiesManager() {
  const [tree, setTree] = useState<CapabilityTreeResponse | null>(null);
  const [activeCapabilityId, setActiveCapabilityId] = useState('');
  const [selectedUseCaseKey, setSelectedUseCaseKey] = useState('');
  const [expandedNodeIds, setExpandedNodeIds] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let ignore = false;
    requestJson<CapabilityTreeResponse>('api/capabilities/tree')
      .then((response) => {
        if (ignore) {
          return;
        }
        setTree(response);
        const firstCapability = response.root.children.find((node) => node.type === 'capability');
        const firstUseCase = response.useCases[0];
        setActiveCapabilityId(firstCapability?.id ?? '');
        setSelectedUseCaseKey(firstUseCase ? useCaseKey(firstUseCase) : '');
        setExpandedNodeIds(defaultExpandedNodeIds(response.root));
      })
      .catch((caught: unknown) => setError(errorMessage(caught)))
      .finally(() => {
        if (!ignore) {
          setLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, []);

  const capabilityNodes = useMemo(
    () => tree?.root.children.filter((node) => node.type === 'capability') ?? [],
    [tree],
  );

  const activeCapability = useMemo(() => {
    return capabilityNodes.find((node) => node.id === activeCapabilityId) ?? capabilityNodes[0] ?? null;
  }, [activeCapabilityId, capabilityNodes]);

  const selectedUseCase = useMemo(() => {
    return tree?.useCases.find((useCase) => useCaseKey(useCase) === selectedUseCaseKey) ?? null;
  }, [selectedUseCaseKey, tree]);

  const selectCapability = (capability: DocNode) => {
    setActiveCapabilityId(capability.id);
    setExpandedNodeIds((current) => new Set(current).add(capability.id));
    const firstUseCase = firstUseCaseInNode(capability);
    if (firstUseCase) {
      setSelectedUseCaseKey(useCaseKey(firstUseCase));
    }
  };

  const toggleNode = (nodeId: string) => {
    setExpandedNodeIds((current) => {
      const next = new Set(current);
      if (next.has(nodeId)) {
        next.delete(nodeId);
      } else {
        next.add(nodeId);
      }
      return next;
    });
  };

  if (loading) {
    return <Shell><div className="status-panel">Loading capabilities...</div></Shell>;
  }

  if (error && !tree) {
    return <Shell><div className="status-panel status-panel-error">{error}</div></Shell>;
  }

  return (
    <Shell>
      <header className="manager-header">
        <div>
          <p className="eyebrow">Merged docs/capabilities</p>
          <h1>Capability Manager</h1>
        </div>
        <RepositoryList repositories={tree?.repositories ?? []} />
      </header>

      {capabilityNodes.length > 0 && (
        <nav className="capability-tabs" aria-label="Software capabilities">
          {capabilityNodes.map((capability) => (
            <button
              key={capability.id}
              type="button"
              className={capability.id === activeCapability?.id ? 'capability-tab active' : 'capability-tab'}
              onClick={() => selectCapability(capability)}
            >
              {capability.name}
            </button>
          ))}
        </nav>
      )}

      <main className="manager-grid">
        <aside className="tree-pane" aria-label="Capability tree">
          {activeCapability ? (
            <TreeNodeView
              node={activeCapability}
              depth={0}
              selectedUseCaseKey={selectedUseCaseKey}
              expandedNodeIds={expandedNodeIds}
              onToggleNode={toggleNode}
              onSelectUseCase={(useCase) => setSelectedUseCaseKey(useCaseKey(useCase))}
            />
          ) : (
            <div className="empty-pane">No capability folders found.</div>
          )}
        </aside>

        <section className="detail-pane" aria-label="Use-case workspace">
          {selectedUseCase ? <UseCasePanel useCase={selectedUseCase} /> : <div className="empty-pane">Select a use case.</div>}
        </section>
      </main>
    </Shell>
  );
}

function Shell({ children }: { children: React.ReactNode }) {
  return <div className="capabilities-app">{children}</div>;
}

function RepositoryList({ repositories }: { repositories: RepositorySummary[] }) {
  if (repositories.length === 0) {
    return null;
  }
  return (
    <div className="repository-list" aria-label="Configured repositories">
      <p className="eyebrow">Repositories</p>
      {repositories.map((repository) => (
        <details className="repository-item" key={repository.name}>
          <summary>{repository.name}</summary>
          <a href={repository.url} target="_blank" rel="noreferrer">{repository.url}</a>
          <span>{repository.source}</span>
        </details>
      ))}
    </div>
  );
}

function TreeNodeView({
  node,
  depth,
  selectedUseCaseKey,
  expandedNodeIds,
  onToggleNode,
  onSelectUseCase,
}: {
  node: DocNode;
  depth: number;
  selectedUseCaseKey: string;
  expandedNodeIds: Set<string>;
  onToggleNode: (nodeId: string) => void;
  onSelectUseCase: (useCase: UseCaseDetails) => void;
}) {
  const hasChildren = node.children.length > 0;
  const isExpanded = expandedNodeIds.has(node.id);
  const useCase = node.useCase;
  const isSelected = useCase ? useCaseKey(useCase) === selectedUseCaseKey : false;

  return (
    <div className="tree-node">
      <div className={isSelected ? 'tree-row selected' : `tree-row ${node.type}`} style={{ paddingLeft: depth * 16 }}>
        <button
          type="button"
          className="icon-button"
          aria-label={`Toggle ${node.name}`}
          disabled={!hasChildren}
          onClick={() => onToggleNode(node.id)}
        >
          {hasChildren ? (isExpanded ? 'v' : '>') : ''}
        </button>

        <button
          type="button"
          className="node-name"
          onClick={() => {
            if (useCase) {
              onSelectUseCase(useCase);
            } else if (hasChildren) {
              onToggleNode(node.id);
            }
          }}
        >
          <span className="node-type">{node.type}</span>
          <span>{node.name}</span>
        </button>
      </div>

      {hasChildren && isExpanded && node.children.map((child) => (
        <TreeNodeView
          key={child.id}
          node={child}
          depth={depth + 1}
          selectedUseCaseKey={selectedUseCaseKey}
          expandedNodeIds={expandedNodeIds}
          onToggleNode={onToggleNode}
          onSelectUseCase={onSelectUseCase}
        />
      ))}
    </div>
  );
}

function UseCasePanel({ useCase }: { useCase: UseCaseDetails }) {
  return (
    <>
      <div className="detail-toolbar">
        <div>
          <p className="breadcrumb">{useCase.useCasePath}</p>
          <h2>{useCase.useCaseId}</h2>
          <p className="source-line">{useCase.repositoryName}</p>
        </div>
      </div>

      {useCase.ucMarkdown && <MarkdownDocument markdown={useCase.ucMarkdown} />}

      <section className="document-section">
        <h3>Acceptance Criteria</h3>
        <pre className="code-window">{useCase.featureText}</pre>
      </section>

      {useCase.scenarios.length > 0 && (
        <section className="document-section">
          <h3>Scenarios</h3>
          <div className="scenario-list">
            {useCase.scenarios.map((scenario) => (
              <details className="scenario-card" key={scenario.id}>
                <summary>{scenario.name}</summary>
                <pre>{scenario.text}</pre>
              </details>
            ))}
          </div>
        </section>
      )}

      {useCase.plantUmlText && (
        <section className="document-section">
          <h3>Use Case Diagram</h3>
          <pre className="code-window">{useCase.plantUmlText}</pre>
        </section>
      )}
    </>
  );
}

function MarkdownDocument({ markdown }: { markdown: string }) {
  const blocks = markdown.split('\n');
  return (
    <article className="markdown-document">
      {blocks.map((line, index) => {
        if (line.startsWith('# ')) {
          return <h1 key={index}>{line.slice(2)}</h1>;
        }
        if (line.startsWith('## ')) {
          return <h2 key={index}>{line.slice(3)}</h2>;
        }
        if (line.startsWith('### ')) {
          return <h3 key={index}>{line.slice(4)}</h3>;
        }
        if (line.trim().length === 0) {
          return <div className="markdown-space" key={index} />;
        }
        return <p key={index}>{line}</p>;
      })}
    </article>
  );
}

async function requestJson<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, options);
  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`;
    try {
      const body = await response.json();
      message = body.detail ?? body.message ?? body.title ?? message;
    } catch {
      message = await response.text();
    }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}

function defaultExpandedNodeIds(root: DocNode) {
  const ids = new Set<string>();
  const visit = (node: DocNode) => {
    if (node.type !== 'use-case') {
      ids.add(node.id);
    }
    node.children.forEach(visit);
  };
  visit(root);
  return ids;
}

function firstUseCaseInNode(node: DocNode): UseCaseDetails | null {
  if (node.useCase) {
    return node.useCase;
  }
  for (const child of node.children) {
    const useCase = firstUseCaseInNode(child);
    if (useCase) {
      return useCase;
    }
  }
  return null;
}

function useCaseKey(useCase: UseCaseDetails) {
  return `${useCase.repositoryName}:${useCase.relativePath}`;
}

function errorMessage(caught: unknown) {
  return caught instanceof Error ? caught.message : 'Unexpected error';
}
