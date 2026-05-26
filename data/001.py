import os
import json
import random
import re

# ================== PENGATURAN ==================
LINKS_PER_TITLE = 15  # Jumlah link per title (default 10)
POSTERS = [
    "https://raw.githubusercontent.com/cemmekx096-cmd/Project69/main/.ide/markup_1000044239.png",
    # Tambahkan link poster lain di sini jika ada
]
# =================================================

def list_txt_files():
    txt_files = [f for f in os.listdir() if f.endswith(".txt")]
    if not txt_files:
        print("Tidak ada file .txt di folder ini.")
        exit()
    print("Daftar file .txt:")
    for idx, f in enumerate(txt_files, 1):
        print(f"{idx}. {f}")
    return txt_files

def select_file(txt_files):
    choice = input("Masukkan nomor file yang ingin diproses: ")
    if not choice.isdigit() or int(choice) < 1 or int(choice) > len(txt_files):
        print("Pilihan tidak valid.")
        exit()
    return txt_files[int(choice)-1]

def extract_links(file_path):
    links = []
    link_pattern = re.compile(r"https?://\S+")
    with open(file_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            match = link_pattern.search(line)
            if match:
                links.append(match.group())
    return links

def generate_titles(base_name, total_links, links_per_title):
    total_titles = (total_links + links_per_title - 1) // links_per_title
    titles = [f"{base_name}_{i+1}" for i in range(total_titles)]
    return titles

def generate_json(links, titles, posters, links_per_title):
    data = []
    for idx, title in enumerate(titles):
        start = idx * links_per_title
        end = start + links_per_title
        episode_links = links[start:end]
        poster = random.choice(posters)
        episodes = []
        for ep_idx, link in enumerate(episode_links, 1):
            # Format satu baris per episode
            episodes.append({
                "episode": f"{ep_idx:02}",
                "name": f"Episode {ep_idx}",
                "url": link
            })
        data.append({
            "title": title,
            "poster": poster,
            "episodes": episodes
        })
    return data

def save_json(json_data, output_file):
    # Custom encoder supaya setiap episode satu baris
    with open(output_file, "w", encoding="utf-8") as f:
        f.write("[\n")
        for t_idx, title_block in enumerate(json_data):
            f.write("  {\n")
            f.write(f"    \"title\": \"{title_block['title']}\",\n")
            f.write(f"    \"poster\": \"{title_block['poster']}\",\n")
            f.write(f"    \"episodes\": [\n")
            for e_idx, ep in enumerate(title_block["episodes"]):
                line = f'      {{ "episode": "{ep["episode"]}", "name": "{ep["name"]}", "url": "{ep["url"]}" }}'
                if e_idx != len(title_block["episodes"]) - 1:
                    line += ","
                line += "\n"
                f.write(line)
            f.write("    ]\n")
            if t_idx != len(json_data) - 1:
                f.write("  },\n")
            else:
                f.write("  }\n")
        f.write("]\n")

def main():
    txt_files = list_txt_files()
    file_path = select_file(txt_files)
    links = extract_links(file_path)
    
    if not links:
        print("Tidak ada link ditemukan di file.")
        return
    
    print(f"{len(links)} link ditemukan di file '{file_path}'.")
    base_name = input("Masukkan base title: ").strip()
    if not base_name:
        print("Base title tidak boleh kosong.")
        return
    
    titles = generate_titles(base_name, len(links), LINKS_PER_TITLE)
    json_data = generate_json(links, titles, POSTERS, LINKS_PER_TITLE)
    
    output_file = f"{base_name}.json"
    save_json(json_data, output_file)
    
    print(f"File JSON berhasil dibuat: {output_file}")

if __name__ == "__main__":
    main()