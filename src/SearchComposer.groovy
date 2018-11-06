

import org.langera.audiofix.Repository
import org.langera.audiofix.composer.Composer

File dbFile = new File('/Users/alanger/dev/audiofix/Composer.db')

Repository<Composer> db = new Repository<>(new Composer())

db.load(dbFile)


boolean done = false

while (!done) {

    print "> "
    String line = System.in.newReader().readLine()
    if (line.equalsIgnoreCase('DONE')) {
        done = true
    }
    else {
        Set<Composer> composers = db.findAll(line)
        println "$line:"
        composers.each {
            println "\t${it.id()}: $it.name ($it.years)"
        }
    }
}

