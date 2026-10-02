import json, glob
from pathlib import Path

out_dir = Path(r'C:\Yagyesh-Other\Projects\Paytm_Money\graphify-out')
chunks = sorted(glob.glob(str(out_dir / '.graphify_chunk_*.json')))
all_nodes, all_edges, all_hyperedges = [], [], []
total_in, total_out = 0, 0
for c in chunks:
    d = json.loads(Path(c).read_text(encoding='utf-8'))
    all_nodes += d.get('nodes', [])
    all_edges += d.get('edges', [])
    all_hyperedges += d.get('hyperedges', [])
    total_in += d.get('input_tokens', 0)
    total_out += d.get('output_tokens', 0)

merged_sem = {
    'nodes': all_nodes,
    'edges': all_edges,
    'hyperedges': all_hyperedges,
    'input_tokens': total_in,
    'output_tokens': total_out,
}
(out_dir / '.graphify_semantic.json').write_text(json.dumps(merged_sem, indent=2, ensure_ascii=False), encoding='utf-8')
print(f'Semantic merge complete: {len(all_nodes)} nodes')

ast_path = out_dir / '.graphify_ast.json'
ast = json.loads(ast_path.read_text(encoding='utf-8')) if ast_path.exists() else {'nodes':[], 'edges':[]}
sem_path = out_dir / '.graphify_semantic.json'
sem = json.loads(sem_path.read_text(encoding='utf-8')) if sem_path.exists() else {'nodes':[], 'edges':[]}

seen = {n['id'] for n in ast['nodes']}
merged_nodes = list(ast['nodes'])
for n in sem['nodes']:
    if n['id'] not in seen:
        merged_nodes.append(n)
        seen.add(n['id'])

merged_final = {
    'nodes': merged_nodes,
    'edges': ast['edges'] + sem['edges'],
    'hyperedges': sem.get('hyperedges', []),
    'input_tokens': sem.get('input_tokens', 0),
    'output_tokens': sem.get('output_tokens', 0),
}
(out_dir / '.graphify_extract.json').write_text(json.dumps(merged_final, indent=2, ensure_ascii=False), encoding='utf-8')
print(f'Final merge complete: {len(merged_nodes)} nodes')