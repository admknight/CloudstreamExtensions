import os
import re

def extract_gradle_field(content, field):
    match = re.search(rf"{field}\s*=\s*\"([^\"]*)\"", content)
    if match: return f'{field} = \"{match.group(1)}\"'

    match = re.search(rf"{field}\s*=\s*listOf\(([^)]*)\)", content)
    if match: return f'{field} = listOf({match.group(1)})'

    match = re.search(rf"{field}\s*=\s*(\d+)", content)
    if match: return f'{field} = {match.group(1)}'

    match = re.search(rf"{field}\s*=\s*(true|false)", content)
    if match: return f'{field} = {match.group(1)}'

    return None

def robust_format(content):
    # List of keywords that usually start a new statement in Gradle KTS
    keywords = [
        r"implementation\(", r"api\(", r"compileOnly\(", r"kapt\(", r"ksp\(",
        r"testImplementation\(", r"androidTestImplementation\(",
        r"val\s+", r"var\s+", r"buildConfigField\(", r"manifestPlaceholders\s+=", r"resValue\(",
        r"cloudstream\(", r"cloudstream\s*\{", r"dependencies\s*\{", r"android\s*\{", r"defaultConfig\s*\{", r"buildFeatures\s*\{"
    ]
    formatted = content
    for kw_pattern in keywords:
        formatted = re.sub(rf'([)"\w])\s+({kw_pattern})', r'\1\n    \2', formatted)
    return formatted

for root, dirs, files in os.walk('.'):
    for file in files:
        if file == 'build.gradle.kts' and root != '.':
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                lines = f.readlines()

            content = ''.join(lines)
            if len(lines) < 5 or (len(lines) < 20 and len(content) > 500):
                print(f'Fixing: {path}')

                version_match = re.search(r'version\s*=\s*(\d+)', content)
                version = version_match.group(1) if version_match else '1'

                cloudstream_match = re.search(r'cloudstream\s*\{(.*?)\}', content, re.DOTALL)
                fields = []
                if cloudstream_match:
                    inner = cloudstream_match.group(1)
                    potential_fields = ['language', 'authors', 'description', 'status', 'tvTypes', 'iconUrl', 'requiresResources', 'isCrossPlatform']
                    for pf in potential_fields:
                        field_str = extract_gradle_field(inner, pf)
                        if field_str:
                            fields.append(field_str)

                new_content = f'version = {version}\n\ncloudstream {{\n'
                for field in fields:
                    new_content += f'    {field}\n'
                new_content += '}\n'

                if 'dependencies {' in content:
                    dep_match = re.search(r'dependencies\s*\{(.*?)\}', content, re.DOTALL)
                    if dep_match:
                        deps_inner = dep_match.group(1).strip()
                        new_content += f'\ndependencies {{\n    {robust_format(deps_inner)}\n}}\n'

                # Always add android block to ensure buildConfig
                new_content += '\nandroid {\n    buildFeatures {\n        buildConfig = true\n    }\n}\n'

                with open(path, 'w', encoding='utf-8') as f:
                    f.write(new_content)
