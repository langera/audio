import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.langera.audiofix.Repository
import org.langera.audiofix.composer.Composer

@Grab(group='org', module='jaudiotagger', version='2.0.3')

import java.util.logging.Level
import java.util.logging.Logger

Logger.getLogger('').handlers.each { it.setLevel(Level.SEVERE)}

File dir = new File('/Volumes/Ori Langer/Music/EMusicClassical_MP3/')
File dbFile = new File('/Users/alanger/dev/audiofix/Composer.db')

Repository<Composer> db = new Repository<>(new Composer())

db.load(dbFile)


int found = 0
int zeroResult = 0
int multipleResult = 0



dir.eachFileRecurse { f ->

    try {
        if (f.name.endsWith('.m4a') || f.name.endsWith('.mp3')) {
            AudioFile audioFile = AudioFileIO.read(f)
            def tag = audioFile.getTagOrCreateDefault()
            String composer = tag.getFirst(FieldKey.COMPOSER)
            Set<Composer> composers = db.findAll(composer)
            if (composers.size() == 0) {
                zeroResult++
                println "NONE $composer ==> ${composers.size()}  - $f.canonicalPath"

            }
            if (composers.size() == 1) {
                found++
                println "MATCH $composer ==> ${composers[0].name} ( ${composers[0].years})"
            }
            if (composers.size() > 1) {
                multipleResult++
                println "MANY $composer ==> ${composers.size()}  - $f.canonicalPath"
            }
        }
    } catch (Exception e) {
        println "ERROR $f.canonicalPath: $e.message"
    }
}
println "TOTAL ${found + zeroResult + multipleResult}, Found a match $found, No result $zeroResult, Many results $multipleResult"

