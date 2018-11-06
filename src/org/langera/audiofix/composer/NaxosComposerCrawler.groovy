package org.langera.audiofix.composer

import groovyx.net.http.HTTPBuilder

@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')

class NaxosComposerCrawler {

    private Closure<Void> listener = { String name -> println name }

    void crawl(Closure<Void> composerNameListener) {
        List toCrawl = []

        ('A'..'Z').each { c ->
            toCrawl << "http://www.naxos.com/composerlist.asp?composer_id=$c&show=all&composer=Classical%20Composer"
        }

        while (!toCrawl.isEmpty()) {
            String url = toCrawl.remove(0)
            listener.call(url)
            def http = new HTTPBuilder(url)
            http.getClient().getParams().setParameter("http.connection.timeout", new Integer(30 * 1000))
            http.getClient().getParams().setParameter("http.socket.timeout", new Integer(120 * 1000))

            try {
                def html = http.get([:])

                if (!url.contains('whichpage=')) {
                    toCrawl = parseNextLinks(html, toCrawl)
                }
                parseComposerNames(html, composerNameListener)
            } catch (e) {
                e.printStackTrace()
                toCrawl.add(url)
            }
        }
    }

    private String toFullUrl(String url) {
        return "http://www.naxos.com${url.replaceAll(' ','%20')}"
    }

    private void parseComposerNames(Object html, Closure<Void> listener) {
        html."**".findAll
        { it.name().equalsIgnoreCase('a') && it.@alt.text().isEmpty() && it.@href.text().startsWith("/person/") }.
                each {
                    def matcher = (it.@title.text() =~ /\((.*)\)/)
                    if (matcher.find()) {
                        String name = it.text()
                        String url = toFullUrl(it.@href.text())
                        String years = matcher[0][1]
                        listener.call(name, url, years)
                    }
                }
    }

    private List parseNextLinks(html, List toCrawl) {
        html."**".findAll
        { it.name().equalsIgnoreCase('a') && it.@href.text().contains("composerlist.asp?whichpage=") }.
                each {
                    def url = toFullUrl(it.@href.text())
                    if (!toCrawl.contains(url)) {
                        toCrawl = toCrawl.plus(0, url)
                    }
                }
        return toCrawl
    }


    public static void main(String[] args) {
        new NaxosComposerCrawler().crawl { n, u, y -> println n + " - " + u + " - " + y }
    }
}
