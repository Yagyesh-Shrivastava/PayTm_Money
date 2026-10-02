import sys, json
from graphify.build import build_from_json
from graphify.cluster import cluster, score_all
from graphify.analyze import god_nodes, surprising_connections, suggest_questions
from graphify.report import generate
from graphify.export import to_json
from pathlib import Path

out_dir = Path(r'C:\Yagyesh-Other\Projects\Paytm_Money\graphify-out')
extract_path = out_dir / '.graphify_extract.json'
detect_path = out_dir / '.graphify_detect.json'

if not extract_path.exists():
    print('ERROR: Extraction file missing')
    sys.exit(1)

extraction = json.loads(extract_path.read_text(encoding='utf-8'))
detection  = json.loads(detect_path.read_text(encoding='utf-8'))

G = build_from_json(extraction, root=r'C:\Yagyesh-Other\Projects\Paytm_Money', directed=False)

if G.number_of_nodes() == 0:
    print('ERROR: Graph is empty')
    sys.exit(1)

communities = cluster(G)
cohesion = score_all(G, communities)
tokens = {'input': extraction.get('input_tokens', 0), 'output': extraction.get('output_tokens', 0)}
gods = god_nodes(G)
surprises = surprising_connections(G, communities)
labels = {cid: 'Community ' + str(cid) for cid in communities}
questions = suggest_questions(G, communities, labels)

wrote = to_json(G, communities, str(out_dir / 'graph.json'))
if not wrote:
    print('ERROR: refused to shrink graph.json')
    sys.exit(1)

report = generate(G, communities, cohesion, labels, gods, surprises, detection, tokens, r'C:\Yagyesh-Other\Projects\Paytm_Money', suggested_questions=questions)
(out_dir / 'GRAPH_REPORT.md').write_text(report, encoding='utf-8')
analysis = {
    'communities': {str(k): v for k, v in communities.items()},
    'cohesion': {str(k): v for k, v in cohesion.items()},
    'gods': gods,
    'surprises': surprises,
    'questions': questions,
}
(out_dir / '.graphify_analysis.json').write_text(json.dumps(analysis, indent=2, ensure_ascii=False), encoding='utf-8')
print(f'Graph: {G.number_of_nodes()} nodes, {G.number_of_edges()} edges, {len(communities)} communities')