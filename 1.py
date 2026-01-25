#!/usr/bin/env python3
"""
Anime Website Structure Analyzer
Automatically analyzes anime streaming sites and generates selectors for Aniyomi extensions
"""

import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin, urlparse
import json
import sys
from collections import Counter

class AnimeSiteAnalyzer:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        })
        self.results = {
            'base_url': self.base_url,
            'homepage': {},
            'search': {},
            'detail': {},
            'episode': {},
            'video_hosts': Counter()
        }
    
    def fetch_page(self, url):
        """Fetch and parse a page"""
        try:
            print(f"Fetching: {url}")
            resp = self.session.get(url, timeout=10)
            resp.raise_for_status()
            return BeautifulSoup(resp.text, 'html.parser')
        except Exception as e:
            print(f"Error fetching {url}: {e}")
            return None
    
    def analyze_homepage(self):
        """Analyze homepage structure"""
        print("\n[1/4] Analyzing Homepage...")
        soup = self.fetch_page(self.base_url)
        if not soup:
            return
        
        # Find anime list containers
        potential_containers = []
        
        # Look for common patterns
        for tag in ['div', 'section', 'ul', 'article']:
            elements = soup.find_all(tag, class_=True)
            for elem in elements:
                # Count anime-like links inside
                links = elem.find_all('a', href=True)
                images = elem.find_all('img', src=True)
                
                if len(links) >= 3 and len(images) >= 3:
                    potential_containers.append({
                        'selector': f"{tag}.{' '.join(elem.get('class', []))}",
                        'links': len(links),
                        'images': len(images)
                    })
        
        if potential_containers:
            # Pick the most likely container
            best = max(potential_containers, key=lambda x: x['links'] + x['images'])
            self.results['homepage']['container'] = best['selector']
            
            # Analyze first anime card
            container = soup.select_one(best['selector'].replace(' ', '.'))
            if container:
                first_link = container.find('a', href=True)
                first_img = container.find('img', src=True)
                
                if first_link:
                    self.results['homepage']['card_link'] = self.get_selector_path(first_link)
                    self.results['homepage']['sample_url'] = urljoin(self.base_url, first_link['href'])
                
                if first_img:
                    self.results['homepage']['card_image'] = self.get_selector_path(first_img)
                
                # Try to find title
                title_elem = first_link.find(['h1', 'h2', 'h3', 'h4', 'span', 'div'])
                if title_elem:
                    self.results['homepage']['card_title'] = title_elem.name
    
    def analyze_search(self, query="naruto"):
        """Analyze search page"""
        print(f"\n[2/4] Analyzing Search (query: {query})...")
        search_url = f"{self.base_url}/?s={query}"
        soup = self.fetch_page(search_url)
        if not soup:
            return
        
        # Similar to homepage analysis
        results = soup.find_all('a', href=True)
        
        # Filter links that might be anime
        anime_links = [a for a in results if 'anime' in a.get('href', '').lower() or 
                       any(keyword in a.text.lower() for keyword in ['episode', 'eps', query.lower()])]
        
        if anime_links:
            first = anime_links[0]
            self.results['search']['result_link'] = self.get_selector_path(first)
            self.results['search']['sample_url'] = urljoin(self.base_url, first['href'])
    
    def analyze_detail_page(self):
        """Analyze anime detail page"""
        print("\n[3/4] Analyzing Detail Page...")
        
        # Use sample URL from homepage or search
        detail_url = self.results.get('homepage', {}).get('sample_url') or \
                     self.results.get('search', {}).get('sample_url')
        
        if not detail_url:
            print("No sample anime URL found. Skipping detail analysis.")
            return
        
        soup = self.fetch_page(detail_url)
        if not soup:
            return
        
        # Find episode list
        episode_links = []
        for a in soup.find_all('a', href=True):
            href = a['href'].lower()
            if any(keyword in href for keyword in ['episode', 'eps', 'ep-', '/e/']):
                episode_links.append(a)
        
        if episode_links:
            self.results['detail']['episode_list_selector'] = 'a[href*="episode"]'
            self.results['detail']['sample_episode_url'] = urljoin(self.base_url, episode_links[0]['href'])
        
        # Find synopsis
        for tag in soup.find_all(['p', 'div'], class_=True):
            text = tag.get_text(strip=True)
            if len(text) > 100 and any(word in text.lower() for word in ['story', 'sinopsis', 'synopsis', 'cerita']):
                self.results['detail']['synopsis_selector'] = self.get_selector_path(tag)
                break
    
    def analyze_episode_page(self):
        """Analyze episode/video page"""
        print("\n[4/4] Analyzing Episode Page...")
        
        episode_url = self.results.get('detail', {}).get('sample_episode_url')
        if not episode_url:
            print("No sample episode URL found. Skipping episode analysis.")
            return
        
        soup = self.fetch_page(episode_url)
        if not soup:
            return
        
        # Find video iframes/embeds
        iframes = soup.find_all('iframe', src=True)
        embeds = soup.find_all(['embed', 'video'], src=True)
        
        video_elements = iframes + embeds
        
        for elem in video_elements:
            src = elem.get('src', '')
            # Detect video host
            if 'streamsb' in src or 'sbembed' in src:
                self.results['video_hosts']['StreamSB'] += 1
            elif 'dood' in src:
                self.results['video_hosts']['Doodstream'] += 1
            elif 'drive.google' in src or 'googleapis' in src:
                self.results['video_hosts']['GoogleDrive'] += 1
            elif 'fembed' in src or 'femax' in src:
                self.results['video_hosts']['Fembed'] += 1
            elif 'mp4upload' in src:
                self.results['video_hosts']['Mp4Upload'] += 1
            else:
                host = urlparse(src).netloc
                if host:
                    self.results['video_hosts'][host] += 1
        
        if video_elements:
            self.results['episode']['video_selector'] = 'iframe[src]'
    
    def get_selector_path(self, element):
        """Generate CSS selector for an element"""
        if element.get('id'):
            return f"#{element['id']}"
        
        classes = element.get('class', [])
        if classes:
            return f"{element.name}.{'.'.join(classes)}"
        
        return element.name
    
    def run_analysis(self):
        """Run complete analysis"""
        print(f"\n{'='*60}")
        print(f"ANALYZING: {self.base_url}")
        print(f"{'='*60}")
        
        self.analyze_homepage()
        self.analyze_search()
        self.analyze_detail_page()
        self.analyze_episode_page()
        
        return self.results
    
    def generate_report(self, output_file):
        """Generate analysis report"""
        domain = urlparse(self.base_url).netloc.replace('www.', '').split('.')[0]
        
        report = f"""
{'='*70}
ANIME WEBSITE ANALYSIS REPORT
{'='*70}
Website: {self.base_url}
Domain: {domain}
Analysis Date: {__import__('datetime').datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

{'='*70}
[1] HOMEPAGE ANALYSIS
{'='*70}
URL: {self.base_url}

Anime List Container: {self.results['homepage'].get('container', 'NOT FOUND')}
Card Link Selector: {self.results['homepage'].get('card_link', 'NOT FOUND')}
Card Image Selector: {self.results['homepage'].get('card_image', 'NOT FOUND')}
Card Title Element: {self.results['homepage'].get('card_title', 'NOT FOUND')}

Sample Anime URL: {self.results['homepage'].get('sample_url', 'N/A')}

{'='*70}
[2] SEARCH PAGE ANALYSIS
{'='*70}
Search URL Pattern: {self.base_url}/?s=QUERY

Result Link Selector: {self.results['search'].get('result_link', 'NOT FOUND')}
Sample Result URL: {self.results['search'].get('sample_url', 'N/A')}

{'='*70}
[3] DETAIL PAGE ANALYSIS
{'='*70}
Episode List Selector: {self.results['detail'].get('episode_list_selector', 'NOT FOUND')}
Synopsis Selector: {self.results['detail'].get('synopsis_selector', 'NOT FOUND')}

Sample Episode URL: {self.results['detail'].get('sample_episode_url', 'N/A')}

{'='*70}
[4] EPISODE/VIDEO PAGE ANALYSIS
{'='*70}
Video Element Selector: {self.results['episode'].get('video_selector', 'NOT FOUND')}

Video Hosts Detected:
"""
        
        if self.results['video_hosts']:
            for host, count in self.results['video_hosts'].most_common():
                report += f"  - {host}: {count} occurrence(s)\n"
        else:
            report += "  - NO VIDEO HOSTS DETECTED\n"
        
        report += f"""
{'='*70}
[5] SUGGESTED KOTLIN CODE SNIPPETS
{'='*70}

```kotlin
// Base configuration
override val name = "{domain.title()}"
override val baseUrl = "{self.base_url}"
override val lang = "id"  // Change to "en" if needed

// Popular anime selectors
override fun popularAnimeSelector() = "{self.results['homepage'].get('container', 'UPDATE_ME')}"
override fun popularAnimeFromElement(element: Element): SAnime {{
    return SAnime.create().apply {{
        setUrlWithoutDomain(element.select("{self.results['homepage'].get('card_link', 'a')}").attr("href"))
        title = element.select("{self.results['homepage'].get('card_title', 'h2')}").text()
        thumbnail_url = element.select("{self.results['homepage'].get('card_image', 'img')}").attr("src")
    }}
}}

// Search
override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {{
    return GET("$baseUrl/?s=$query", headers)
}}

// Episode list
override fun episodeListSelector() = "{self.results['detail'].get('episode_list_selector', 'a[href*=episode]')}"

// Video extraction
override fun videoListSelector() = "{self.results['episode'].get('video_selector', 'iframe[src]')}"
```

{'='*70}
VIDEO EXTRACTORS NEEDED:
{'='*70}
"""
        
        if self.results['video_hosts']:
            report += "\nAdd these dependencies to build.gradle.kts:\n"
            for host in self.results['video_hosts'].keys():
                if host == 'StreamSB':
                    report += '  implementation(project(":lib:streamsb-extractor"))\n'
                elif host == 'Doodstream':
                    report += '  implementation(project(":lib:doodstream-extractor"))\n'
        
        report += f"\n{'='*70}\n"
        report += "END OF ANALYSIS\n"
        report += f"{'='*70}\n"
        
        # Write to file
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(report)
        
        print(f"\n✅ Report saved to: {output_file}")
        print(report)

def main():
    if len(sys.argv) < 2:
        print("Usage: python analyze_anime_site.py <website_url>")
        print("Example: python analyze_anime_site.py https://otakudesu.cloud")
        sys.exit(1)
    
    url = sys.argv[1]
    
    analyzer = AnimeSiteAnalyzer(url)
    results = analyzer.run_analysis()
    
    # Generate output filename
    domain = urlparse(url).netloc.replace('www.', '').split('.')[0]
    output_file = f"{domain}_analysis.txt"
    
    analyzer.generate_report(output_file)
    
    # Also save JSON for programmatic use
    json_file = f"{domain}_analysis.json"
    with open(json_file, 'w', encoding='utf-8') as f:
        json.dump(results, f, indent=2)
    print(f"✅ JSON data saved to: {json_file}")

if __name__ == "__main__":
    main()