import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import groovy.json.JsonOutput

import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.storage.Asset
import org.sonatype.nexus.repository.storage.Query
import org.sonatype.nexus.repository.storage.StorageFacet
import org.sonatype.nexus.repository.storage.StorageTx
import com.orientechnologies.orient.core.sql.OCommandSQL

assert args: 'Missing arguments, must supply repo, group, name, and version'

def request = new JsonSlurper().parseText(args)

assert request.repo: 'repo parameter missing'
assert request.group: 'group parameter missing'
assert request.name: 'name parameter missing'
assert request.version: 'version parameter missing'

log.info("Listing classifiers for ${request.group}:${request.name}:${request.version} in ${request.repo}")

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
    def prefix = request.group.replace('.', '/') + '/' + request.name + '/' + request.version + '/'
    def ret = [
        spec: 0,
        name: request.name,
        group: request.group,
        version: request.version,
        url: repo.url + '/' + prefix,
        classifiers: [:]
    ]
    try {
        tx.begin()

        OCommandSQL cmd = new OCommandSQL(
            'SELECT ' +
              'attributes.maven2.classifier as classifier,' +
              'attributes.content.last_modified as modified,' +
              'attributes.maven2.extension as ext,' +
              'name,' +
              'size,' +
              'attributes.checksum.md5 as md5,' +
              'attributes.checksum.sha1 as sha1' +
            ' ' +
            'FROM asset ' +
            'WHERE (' +
                'attributes.maven2.groupId = :group ' +
                'AND attributes.maven2.artifactId = :name ' +
                'AND attributes.maven2.baseVersion = :version ' +
                'AND attributes.maven2.extension MATCHES \'\\\\b[^.]+\\\\b\' ' + //Only files with one extension, to skip .md5/.sha1 files
            ')'
        )

        tx.db.command(cmd).execute([group: request.group, name: request.name, version: request.version]).each { row ->
            row = toDict(row)
            if (row.ext == 'pom')
                return
            row.name = row.name.substring(prefix.length())
            row.modified = dateFormat.format(row.modified)
            if (row.classifier == null)
                row.classifier = ''

            if (!ret.classifiers.containsKey(row.classifier))
                ret.classifiers[row.classifier] = [:]
            ret.classifiers[row.classifier][row.ext] = row
            row.remove('ext')
            row.remove('classifier')
        }
    } finally {
        tx.close()
    }
    return ret
}
return JsonOutput.toJson(main(request))
