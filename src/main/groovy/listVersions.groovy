import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import groovy.json.JsonOutput

import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.storage.Asset
import org.sonatype.nexus.repository.storage.Query
import org.sonatype.nexus.repository.storage.StorageFacet
import org.sonatype.nexus.repository.storage.StorageTx
import com.orientechnologies.orient.core.sql.OCommandSQL

assert args: 'Missing arguments, must supply repo, group, and name'

def request = new JsonSlurper().parseText(args)

assert request.repo: 'repo parameter missing'
assert request.group: 'group parameter missing'
assert request.name: 'name parameter missing'

log.info("Listing versions for ${request.group}:${request.name} in ${request.repo}")

def toDict(row) {
    def ret = [:]
    row.fieldNames().each { ret[it] = row.field(it) }
    return ret
}

def main(request) {
    Repository repo = repository.repositoryManager.get(request.repo)
    StorageFacet storage = repo.facet(StorageFacet)
    StorageTx tx = storage.txSupplier().get()

    def dateFormat = new SimpleDateFormat('MM/DD/YY hh:mm:ss a')
    def prefix = request.group.replace('.', '/') + '/' + request.name + '/'

    def ret = [
        spec: 0,
        name: request.name,
        group: request.group,
        url: repo.url + '/' + prefix,
        versions: [:]
    ]

    //TODO: See if we can cache listClassifiers and include a checksum of some kind in this query.

    try {
        tx.begin()

        OCommandSQL cmd = new OCommandSQL(
            'SELECT ' +
              'DISTINCT(attributes.maven2.baseVersion) as version,' +
              'MAX(attributes.content.last_modified) as modified ' +
            ' ' +
            'FROM asset ' +
            'WHERE (' +
                'attributes.maven2.groupId = :group ' +
                'AND attributes.maven2.artifactId = :name' +
            ') ' +
            'GROUP BY attributes.maven2.baseVersion'
        )
        tx.db.command(cmd).execute([group: request.group, name: request.name]).each { row ->
            row = toDict(row)
            ret.versions[row.version] = dateFormat.format(row.modified)
        }
        ret.versions = ret.versions.sort()
    } finally {
        tx.close()
    }

    return JsonOutput.toJson(ret)
}

return main(request)
