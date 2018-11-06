import org.langera.audiofix.Repository
import org.langera.audiofix.Text
import org.langera.audiofix.composer.BbcMagazineComposerCrawler
import org.langera.audiofix.composer.Composer

import org.langera.audiofix.composer.NaxosComposerCrawler

import static org.langera.audiofix.Text.normalize
import static org.langera.audiofix.Text.strongNormalize

File naxosFile = new File('/Users/alanger/dev/audiofix/NaxosComposerDB.csv')
File bbcFile = new File('/Users/alanger/dev/audiofix/BBCComposerDB.csv')
File dbFile = new File('/Users/alanger/dev/audiofix/Composer.db')
File configFile = new File('/Users/alanger/dev/audiofix/Composer.config')

Repository<Composer> db = new Repository<>(new Composer())

db.load(dbFile)
println 'LOAD DB'

crawlNaxos(naxosFile, db)
crawlBbc(bbcFile)

if (db.isEmpty()) {

    db.load(naxosFile)
    println 'LOAD DB from Naxos'

    mergeBBcToDb(bbcFile, db)
    println 'MERGED BBC'
}

clearConditions(db)

applyConfig(configFile, db)

cleanup(db)

db.patchSearch()

db.store(dbFile)
println 'STORE DB'

db.report()

println 'DONE'


private void mergeBBcToDb(File bbcFile, Repository<Composer> db) {
    bbcFile.withReader { input ->

        input.eachLine { line ->
            Composer bbcComposer = new Composer().unserialize(line)

            String name = bbcComposer.name
            String url = bbcComposer.ref.first()
            String lastName = (name.trim() =~ /.*\s+([^\s]*)/)[0][1]
            String normalizedLastName = strongNormalize(lastName)

            List<String> matchingKeys = db.keys().findAll() { it.startsWith(normalizedLastName) }
            if (matchingKeys.size() == 1) {
                String key = matchingKeys.first()
                List<Composer> composers = db.remove(key)
                if (composers.size() > 1) {
                    handleMultipleResults(db, composers, url, lastName, name, name.replaceAll(lastName, '').trim())
                } else {
                    String fullName = buildName(lastName, name)
                    handleSingleResult(db, composers.first(), url, fullName)
                }
            } else if (matchingKeys.size() == 0) {
                println "Could not find Composer to match $lastName"
            } else {
                List<Composer> composers = []
                matchingKeys.collect() { key -> composers.addAll(db.remove(key)) }
                handleMultipleResults(db, composers, url, lastName, name, name.replaceAll(lastName, '').trim())
            }
        }
    }
}

private void crawlNaxos(File naxosFile, Repository<Composer> db) {
    if (!naxosFile.exists()) {
        naxosFile.withPrintWriter { out ->
            NaxosComposerCrawler crawler = new NaxosComposerCrawler()
            crawler.crawl { name, url, years ->
                println "$name ($years)"
                try {
                    if (!excluded(name)) {
                        new Composer(name: name, years: years, ref: [url]).serialize(out).addTo(db)
                    }
                } catch (e) {
                    e.printStackTrace()
                }
            }
        }
    }
    else {
        println 'Naxos already crawled'
    }
}

private void crawlBbc(File bbcFile) {
    if (!bbcFile.exists()) {
        bbcFile.withPrintWriter { out ->
            BbcMagazineComposerCrawler crawler = new BbcMagazineComposerCrawler()
            crawler.crawl { name, url ->
                println "$name, $url"
                new Composer(name: name, ref: [url]).serialize(out)
            }
        }
    } else {
        println 'BBC already crawled'
    }
}

private void handleMultipleResults(Repository<Composer> db, List<Composer> composers,
                           String url, String lastName, String name, String leftOver) {
    if (leftOver.isEmpty()) {
        if (!handleMultipleResultsByDistance(db, composers, lastName, name, url)) {
            println "Could not find Composer to match $name. too many options - $composers"
        }
    } else {
        String firstName = (leftOver =~ /([^\s]+)\s*/)[0][1]
        String normalizedFirstName = normalize(firstName)
        if (!['de','la','von','of'].contains(normalizedFirstName)) {
            List<Composer> finerResults = composers.findAll() { normalize(it.name).contains(normalizedFirstName) }
            if (finerResults.size() == 1) {
                String fullName = buildName(lastName, name)
                handleSingleResult(db, finerResults.first(), url, fullName)
            } else if (finerResults.size() == 0) {
                println "Could not find Composer to match $lastName, $normalizedFirstName"
            } else {
                handleMultipleResults(db, finerResults, url, lastName, name,
                        leftOver.replaceAll(firstName, '').trim())
            }
            addRemainingBackToDb(composers, finerResults, db)
        } else {
            handleMultipleResults(db, composers, url, lastName, name,
                    leftOver.replaceAll(firstName, '').trim())
        }
    }

}

private static List<Composer> addRemainingBackToDb(List<Composer> composers, List<Composer> finerResults, Repository<Composer> db) {
    composers.each {
        if (!finerResults.contains(it)) {
            it.addTo(db)
        }
    }
}


private static boolean handleMultipleResultsByDistance(Repository<Composer> db, List<Composer> composers,
                                                       String lastName, String name, String url) {
    String fullName = buildName(lastName, name)
    List<Integer> scores = composers.collect() { Text.levenshteinDistance(it.name, fullName) }
    int min = scores.min()
    if (scores.findAll() { it >  min + 4 }.size() == scores.size() - 1) { // if min + 4 still smaller than second score
        scores.eachWithIndex { int score, int i ->
            if (score == min) {
                addRemainingBackToDb(composers, [composers[i]], db)
                handleSingleResult(db, composers[i], url, fullName)
            }
        }
        return true
    }
    return false
}

private static void handleSingleResult(Repository<Composer> db, Composer composer, String url, String fullName) {
    List<String> ref = (url == null || composer.ref.contains(url)) ?
            composer.ref : composer.ref + url
    composer.name = fullName
    composer.ref = ref
    db.add(composer)
}


private static String buildName(String lastName, String name) {
    String firstNames = name.replace(lastName, '').trim()
    return "$lastName, $firstNames"
}


private static boolean excluded(String name) {
    name.contains('(')
}

private void cleanup(final Repository<Composer> repository) {
    repository.values().flatten().each { Composer composer ->
        String name = composer.name
        if (name.endsWith(',')) {
            println "CLEANUP $name"
            String modified = name.substring(0, name.length() - 1).trim()
            composer.name = modified
        }
    }
}

private static void applyConfig(final File configFile, final Repository<Composer> repository) {
    configFile.eachLine { line ->
        if (repository.applyConfig(line,
                { details ->
                    String name
                    String years = ''
                    details.split(/\+/).each {
                        if (it.startsWith('name:')) {
                            name = it.substring(it.indexOf(':') + 1)
                        }
                        if (it.startsWith('years:')) {
                            years = it.substring(it.indexOf(':') + 1)
                        }
                    }
                    if (!name) {
                        throw new UnsupportedOperationException(line)
                    }
                    new Composer(name: name, years: years)
                },
                { replacement, composer ->
                            String oldName = composer.name
                            String newName = oldName.replace(replacement[0], replacement[1])
                            if (newName != oldName) {
                                composer.name = newName
                                println "REPLACE $oldName with $composer.name"
                                return true
                            }
                            return false
                })) {
            println "APPLIED $line"
        }
    }
}

private void clearConditions(final Repository<Composer> repository) {
    repository.clearConditions()
}