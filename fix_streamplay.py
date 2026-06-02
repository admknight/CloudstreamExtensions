import os
import re

base_dir = r"C:/Users/mzhsp/Desktop/github/CloudstreamExtensions/International/StreamPlay/src/main/kotlin/com/admknight/streamplay"

def fix_file(file_path, package_name):
    if not os.path.exists(file_path):
        return
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Fix package
    content = re.sub(r'^package com\.[pP]hisher98(\.settings)?', f'package {package_name}', content, flags=re.MULTILINE)

    # Fix internal imports
    content = re.sub(r'import com\.[pP]hisher98\.', f'import com.admknight.streamplay.', content)

    # Fix signature mismatches and syntax
    content = content.replace('val this.score = ', 'val scoreValue = ')
    content = content.replace('this.score = Score.from10(rating)', 'this.score = scoreValue')

    if "settings" in package_name:
        # Ensure common imports for settings
        if "import com.admknight.streamplay.StreamPlayPlugin" not in content:
            content = content.replace("import ", "import com.admknight.streamplay.StreamPlayPlugin\nimport com.admknight.streamplay.BuildConfig\nimport com.admknight.streamplay.*\nimport ", 1)

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

# Process all files in main dir and subdirs
for root, dirs, files in os.walk(base_dir):
    for f in files:
        if f.endswith(".kt"):
            pkg = "com.admknight.streamplay"
            if "settings" in root:
                pkg = "com.admknight.streamplay.settings"
            fix_file(os.path.join(root, f), pkg)
