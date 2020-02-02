import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import groovy.json.JsonOutput

import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.storage.Asset
import org.sonatype.nexus.repository.storage.Query
import org.sonatype.nexus.repository.storage.StorageFacet
import org.sonatype.nexus.repository.storage.StorageTx

assert args: 'Missing arguments, must supply repo, group, and name'

def request = new JsonSlurper().parseText(args)

assert request.repo: 'repo parameter missing'
assert request.group: 'group parameter missing'
assert request.name: 'name parameter missing'

log.info("Listing asset versions for ${request.group}:${request.name} in ${request.repo}")

Repository repo = repository.repositoryManager.get(request.repo)
StorageFacet storage = repo.facet(StorageFacet)
StorageTx tx = storage.txSupplier().get()
def ret = [
    artifactId: request.name,
    name: request.name,
    groupId: request.group,
    versions: []
]
def versions = [:]
def dateFormat = new SimpleDateFormat('MM/DD/YY hh:mm:ss a')

try {
    tx.begin()
    Query.Builder builder = Query.builder()
        .where('attributes.maven2.groupId').eq(request.group)
        .and('attributes.maven2.artifactId').eq(request.name)
    if (request.version != null) {
        log.info("  Version: ${request.version}")
        builder.and('attributes.maven2.baseVersion').eq(request.version)
    }

    if (request.classifier != null) {
        log.info("  Classifier: ${request.classifier}")
        builder.and('attributes.maven2.classifier').eq(request.classifier)
    }

    if (request.ext != null) {
        log.info("  Extension: ${request.ext}")
        builder.and('attributes.maven2.extension').eq(request.ext)
    } else {
        builder.and('attributes.maven2.extension MATCHES ').param('\\b[^.]+\\b') // Don't need any ext.md5/ext.sha1 files
    }

    log.info('Query: ' + builder.build().getWhere())

    tx.findAssets(builder.build(), [repo]).collect { Asset asset ->
        def attribs = asset.attributes()
        def mvn = attribs.get('maven2')
        def ver = mvn.get('baseVersion')
        def classifier = mvn.get('classifier') == null ? '' : mvn.get('classifier')
        def modified = attribs.get('content').get('last_modified')

        if (mvn.get('extension') != 'pom') {
            def version = versions.get(ver)
            if (version == null) {
                version = [version: ver, classifiers: [:]]
                versions.put(ver, version)
            }
            version.classifiers.put(classifier, [
                name: asset.name().substring(asset.name().lastIndexOf('/') + 1),
                path: asset.name(),
                ext: mvn.get('extension'),
                md5: attribs.get('checksum').get('md5'),
                sha1: attribs.get('checksum').get('sha1'),
                time: dateFormat.format(modified)
            ])
        }
    }

    versions.values().each{ ret.versions.add(it) }

} finally {
    tx.close()
}

return JsonOutput.prettyPrint(JsonOutput.toJson(ret))
