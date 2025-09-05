File out = new File( basedir, "target/generated-schemas/example.schema.json" )
assert out.isFile()

def json = new groovy.json.JsonSlurper().parse(out)
assert json.title == 'User'
assert json.description == 'A simple user record'
assert json.properties.id.description == 'Unique identifier'
assert json.properties.name.description == 'Display name'
assert json.properties.tags.description == 'User labels'
