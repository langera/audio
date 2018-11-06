package org.langera.audiofix.composer

import groovy.transform.Canonical
import org.langera.audiofix.Condition
import org.langera.audiofix.DataObject
import org.langera.audiofix.Text

@Canonical
class Composer extends DataObject<Composer> {

    String name
    String years = ''
    List<String> ref = []

    @Override
    String id() {
        return Text.strongNormalize(name)
    }

    @Override
    List<String> keywordsInOrderOfImportance() {
        return name.split(/\s+/).collect { Text.strongNormalize(it) }
    }

    @Override
    Composer get() {
        return new Composer()
    }

    @Override
    Composer serialize(PrintWriter out) {
        out.print(escape(name))
        printDelimeter(out)
        out.print(escape(years))
        printDelimeter(out)
        printCollection(out, escape(ref))
        printDelimeter(out)
        printCollection(out, escape(aliases))
        printDelimeter(out)
        printCollection(out, escape(conditions.collect() { it.toString() }))
        out.println()
        return this
    }


    @Override
    Composer unserialize(String line) {
        String[] tokens = splitDelimeter(line)
        int index = set(tokens, 0,  { name = it })
        index = set(tokens, index,  { years = it })
        index = readCollection(tokens, index, ref)
        index = readCollection(tokens, index, aliases)
        List<String> strCondition = []
        readCollection(tokens, index, strCondition)
        conditions = strCondition.collect { new Condition(it) }
        return this
    }

}