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

Repository repo = repository.repositoryManager.get(request.repo)
StorageFacet storage = repo.facet(StorageFacet)
StorageTx tx = storage.txSupplier().get()

def prefix = request.group.replace('.', '/') + '/' + request.name + '/'

def ret = [
    spec: 1,
    name: request.name,
    group: request.group,
    url: repo.url + '/' + prefix,
    versions: []
]

//TODO: See if we can cache listClassifiers and include a checksum of some kind in this query.

try {
    tx.begin()

    OCommandSQL cmd = new OCommandSQL(
        'SELECT DISTINCT(attributes.maven2.baseVersion) as version ' +
        'FROM asset ' +
        'WHERE (' +
            'attributes.maven2.groupId = :group ' +
            'AND attributes.maven2.artifactId = :name' +
        ')'
    )
    tx.db.command(cmd).execute([group: request.group, name: request.name]).each { row ->
        ret.versions.add(row.field('version'))
    }
    ret.versions = ret.versions.sort()
} finally {
    tx.close()
}

return JsonOutput.prettyPrint(JsonOutput.toJson(ret))
