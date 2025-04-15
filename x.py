import re
import os

def remove_comments(file_path):
    with open(file_path, 'r', encoding='utf-8') as file:
        content = file.read()
    
    # 先处理多行注释
    content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
    
    # 处理单行注释，但保留字符串中的 //
    lines = content.split('\n')
    processed_lines = []
    
    for line in lines:
        # 如果行中包含字符串，需要特殊处理
        if '"' in line or "'" in line:
            # 找到所有字符串的位置
            string_positions = []
            in_string = False
            string_start = 0
            for i, char in enumerate(line):
                if char in ['"', "'"] and (i == 0 or line[i-1] != '\\'):
                    if not in_string:
                        string_start = i
                        in_string = True
                    else:
                        string_positions.append((string_start, i))
                        in_string = False
            
            # 如果找到了字符串，需要保护字符串中的 //
            if string_positions:
                protected_line = line
                for start, end in reversed(string_positions):
                    string_content = line[start:end+1]
                    # 将字符串内容替换为临时标记
                    protected_line = protected_line[:start] + f"__STRING_{start}__" + protected_line[end+1:]
                
                # 移除注释
                protected_line = re.sub(r'//.*$', '', protected_line)
                
                # 恢复字符串内容
                for start, end in string_positions:
                    string_content = line[start:end+1]
                    protected_line = protected_line.replace(f"__STRING_{start}__", string_content)
                
                processed_lines.append(protected_line)
            else:
                processed_lines.append(re.sub(r'//.*$', '', line))
        else:
            # 如果行中没有字符串，直接移除注释
            processed_lines.append(re.sub(r'//.*$', '', line))
    
    # 重新组合内容并移除空行
    content = '\n'.join(line for line in processed_lines if line.strip())
    
    with open(file_path, 'w', encoding='utf-8') as file:
        file.write(content)

def process_directory(directory):
    for root, _, files in os.walk(directory):
        for file in files:
            if file.endswith('.java'):
                file_path = os.path.join(root, file)
                print(f"Processing: {file_path}")
                remove_comments(file_path)

if __name__ == "__main__":
    # 获取当前目录
    current_dir = os.getcwd()
    process_directory(current_dir)
    print("All Java comments have been removed!") 