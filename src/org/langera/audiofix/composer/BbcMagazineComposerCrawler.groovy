package org.langera.audiofix.composer

import groovyx.net.http.HTTPBuilder

@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')


class BbcMagazineComposerCrawler {

    private Closure<Void> listener = { String name -> println name }

    public void crawl(Closure<Void> composerNameListener) {
        List toCrawl = []

        toCrawl << 'http://www.classical-music.com/great-composers/search?title='

        (1..10).each {
            toCrawl << "http://www.classical-music.com/great-composers/search?page=$it&title="
        }

        while (!toCrawl.isEmpty()) {
            String url = toCrawl.remove(0)
            listener.call(url)
            def http = new HTTPBuilder(url)
            def html = http.get([:])

            parseComposerNames(html, composerNameListener)
        }
    }

    private void parseComposerNames(Object html, Closure<Void> composerNameListener) {
        html."**".findAll
        { it.name().equalsIgnoreCase('a') && it.@href.text().startsWith("/topic/") && it.attributes().size() == 1 && !it.text().isEmpty() }.
                each {
                    String name = it.text()
                    name = (name.contains(':')) ? name.substring(0, name.indexOf(':')) : name
                    String url = toFullUrl(it.@href.text())
                    composerNameListener.call(name, url)
                }
    }

    private String toFullUrl(String url) {
        return "http://www.classical-music.com${url.replaceAll(' ','%20')}"
    }

    public static void main(String[] args) {
        new BbcMagazineComposerCrawler().crawl { name, u -> println name + " - " + u }
    }
}
