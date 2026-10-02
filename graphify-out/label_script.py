import sys, json
from graphify.build import build_from_json
from graphify.cluster import score_all
from graphify.analyze import god_nodes, surprising_connections, suggest_questions
from graphify.report import generate
from graphify.export import to_json
from pathlib import Path

out_dir = Path(r'C:\Yagyesh-Other\Projects\Paytm_Money\graphify-out')
extract_path = out_dir / '.graphify_extract.json'
detect_path = out_dir / '.graphify_detect.json'
analysis_path = out_dir / '.graphify_analysis.json'

extraction = json.loads(extract_path.read_text(encoding='utf-8'))
detection  = json.loads(detect_path.read_text(encoding='utf-8'))
analysis   = json.loads(analysis_path.read_text(encoding='utf-8'))

G = build_from_json(extraction, root=r'C:\Yagyesh-Other\Projects\Paytm_Money', directed=False)
communities = {int(k): v for k, v in analysis['communities'].items()}
cohesion = {int(k): v for k, v in analysis['cohesion'].items()}
tokens = {'input': extraction.get('input_tokens', 0), 'output': extraction.get('output_tokens', 0)}

labels = {"0": "Project Configuration"}

questions = suggest_questions(G, communities, labels)

report = generate(G, communities, cohesion, labels, analysis['gods'], analysis['surprises'], detection, tokens, r'C:\Yagyesh-Other\Projects\Paytm_Money', suggested_questions=questions)
(out_dir / 'GRAPH_REPORT.md').write_text(report, encoding='utf-8')
(out_dir / '.graphify_labels.json').write_text(json.dumps({str(k): v for k, v in labels.items()}, ensure_ascii=False), encoding='utf-8')

wrote = to_json(G, communities, str(out_dir / 'graph.json'), community_labels=labels)
print('Report updated with community labels')