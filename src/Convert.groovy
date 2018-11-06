

//File f = new File('/Users/alanger/dev/audiofix/NaxosComposerDB.csv')
//
//List<Composer> list = []
//
//
//f.eachLine { line ->
//    def oldComposer = new OldComposer().unserialize(line)
//    list.add(new Composer(name: oldComposer.name, years: oldComposer.years, ref: oldComposer.ref))
//}
//
//f.withPrintWriter { out ->
//
//    list.each {
//        it.serialize(out)
//    }
//}