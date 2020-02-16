import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import groovy.json.JsonOutput

import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.storage.Asset
import org.sonatype.nexus.repository.storage.Query
import org.sonatype.nexus.repository.storage.StorageFacet
import org.sonatype.nexus.repository.storage.StorageTx
import com.orientechnologies.orient.core.sql.OCommandSQL

assert args: 'Missing arguments, must supply repo, and paths/time pairs'

def request = new JsonSlurper().parseText(args)

assert request.repo: 'repo parameter missing'
assert request.paths: 'paths parameter missing'

log.info("Updating timestamps in ${request.repo}")

def toDict(row) {
    def ret = [:]
    row.fieldNames().each { ret[it] = row.field(it) }
    return ret
}

def main(request) {
    Repository repo = repository.repositoryManager.get(request.repo)
    StorageFacet storage = repo.facet(StorageFacet)
    StorageTx tx = storage.txSupplier().get()

    def ret = [
        spec: 0,
        paths: [:]
    ]


    try {
        tx.begin()

        OCommandSQL cmd = new OCommandSQL(
            'UPDATE asset ' +
              'SET attributes.content.last_modified = DATE(:time) ' +
            ' ' +
            'WHERE (' +
                'name = :path ' +
            ')'
        )
        request.paths.each { path,time ->
            log.info('  Setting ' + path + ' to ' + time)
            tx.db.command(cmd).execute([path: path, time: time]).each { row ->
                ret.paths[path] = row
            }
        }

        tx.commit()
    } finally {
        tx.close()
    }

    return JsonOutput.toJson(ret)
}

return main(request)
