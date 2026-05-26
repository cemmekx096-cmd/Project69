import json

def generate():
    print("=== Universal Player - JSON Generator ===\n")
    
    jumlah_anime = int(input("Mau bikin berapa anime? "))
    anime_list = []

    for a in range(1, jumlah_anime + 1):
        print(f"\n--- Anime {a} ---")
        title = input("Nama anime: ")
        poster = input("Link poster: ")

        jumlah_episode = int(input(f"'{title}' ada berapa episode? "))
        episodes = []

        print(f"Masukkan {jumlah_episode} link video (1 per baris):")
        for e in range(1, jumlah_episode + 1):
            url = input(f"  Episode {e:02d}: ")
            episodes.append({
                "episode": f"{e:02d}",
                "name": f"Episode {e:02d}",
                "url": url
            })

        anime_list.append({
            "title": title,
            "poster": poster,
            "episodes": episodes
        })

        print(f"✅ '{title}' ({jumlah_episode} episode) berhasil ditambahkan!")

    # Simpan ke list.json
    with open("list.json", "w", encoding="utf-8") as f:
        json.dump(anime_list, f, indent=2, ensure_ascii=False)

    print(f"\n✅ Selesai! {jumlah_anime} anime disimpan ke list.json")

if __name__ == "__main__":
    generate()
