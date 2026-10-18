#!/usr/bin/env python3
"""
generate_status.py

Generates site/status.json summarizing current providers, build status,
domain health, and migration state for the static dashboard.
"""

import os
import json
from datetime import datetime, timezone

def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    # Read providers config
    providers_path = os.path.join(root, 'config', 'providers.json')
    with open(providers_path, 'r', encoding='utf-8') as f:
        providers_data = json.load(f)
        
    # Read plugins.json if exists
    plugins_path = os.path.join(root, 'build', 'plugins.json')
    plugins_data = {}
    if os.path.exists(plugins_path):
        with open(plugins_path, 'r', encoding='utf-8') as f:
            for p in json.load(f):
                plugins_data[p['name']] = p
                
    # Read domain health if exists
    domain_health_path = os.path.join(root, 'reports', 'domain-health.json')
    domain_data = {}
    if os.path.exists(domain_health_path):
        with open(domain_health_path, 'r', encoding='utf-8') as f:
            raw_d = json.load(f)
            if isinstance(raw_d, list):
                for item in raw_d:
                    domain_data[item.get('provider')] = item
            elif isinstance(raw_d, dict):
                domain_data = raw_d.get('providers', {})
            
    # Read domains config
    domains_cfg_path = os.path.join(root, 'config', 'domains.json')
    domains_cfg = {}
    if os.path.exists(domains_cfg_path):
        with open(domains_cfg_path, 'r', encoding='utf-8') as f:
            domains_cfg = json.load(f).get('providers', {})

    status_items = []
    for prov in providers_data.get('providers', []):
        name = prov['name']
        plugin_info = plugins_data.get(name, {})
        d_info = domain_data.get(name, {})
        d_cfg = domains_cfg.get(name, {})
        
        status_items.append({
            'name': name,
            'module': prov.get('module'),
            'enabled': prov.get('enabled', False),
            'language': prov.get('language', 'tr'),
            'types': prov.get('types', []),
            'version': plugin_info.get('version'),
            'canonicalDomain': d_cfg.get('canonical'),
            'domainStatus': d_info.get('status', 'unknown'),
            'buildStatus': 'pass' if name in plugins_data else 'not_built',
            'healthStatus': 'healthy' if d_info.get('status') == 'healthy' else 'unknown'
        })
        
    output = {
        'generatedAt': datetime.now(timezone.utc).isoformat(),
        'repository': 'Emre-Kahveci/CloudStreamHub',
        'activeProviderCount': len([p for p in status_items if p['buildStatus'] == 'pass']),
        'totalProviders': len(status_items),
        'providers': status_items
    }
    
    out_path = os.path.join(root, 'site', 'status.json')
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(output, f, indent=2)
        
    print(f'[generate_status] Wrote dashboard status to {out_path}')

if __name__ == '__main__':
    main()
