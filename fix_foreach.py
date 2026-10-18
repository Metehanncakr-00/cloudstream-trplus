import os, re

pattern1 = re.compile(r'([a-zA-Z0-9_.\(\)\[\]\?"\']+?\.select\([^)]+\))\.forEach\s*\{\s*([a-zA-Z0-9_]+)\s*->')
pattern2 = re.compile(r'([a-zA-Z0-9_.\(\)\[\]\?"\']+?\.selectFirst\([^)]+\)\?.select\([^)]+\))\.forEach\s*\{\s*([a-zA-Z0-9_]+)\s*->')
pattern3 = re.compile(r'([a-zA-Z0-9_.\(\)\[\]\?"\']+?\.findAll\([^)]+\))\.forEach\s*\{\s*([a-zA-Z0-9_]+)\s*->')
pattern4 = re.compile(r'([a-zA-Z0-9_.?]+)\.forEach\s*\{\s*([a-zA-Z0-9_]+)\s*->')

def fix_content(content):
    c = pattern1.sub(r'for (\2 in \1) {', content)
    c = pattern2.sub(r'for (\2 in \1) {', c)
    c = pattern3.sub(r'for (\2 in \1) {', c)
    # Be careful with pattern4, it might match too broadly.
    # We will only apply it for known collections that failed, but actually let's just do pattern1, 2, 3 for now, and see if it passes.
    return c

def process_dir(d):
    count = 0
    for root, dirs, files in os.walk(d):
        if '.git' in root or 'build' in root:
            continue
        for f in files:
            if f.endswith('.kt'):
                path = os.path.join(root, f)
                with open(path, 'r', encoding='utf-8') as file:
                    content = file.read()
                
                new_content = fix_content(content)
                
                if new_content != content:
                    with open(path, 'w', encoding='utf-8') as file:
                        file.write(new_content)
                    print(f'Fixed {path}')
                    count += 1
    print(f'Total fixed: {count}')

process_dir('.')
