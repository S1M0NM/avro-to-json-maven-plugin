File out1 = new File( basedir, "target/generated-schemas/optional-union.schema.json" )
assert out1.isFile()

// Basic sanity: file contains Status enum symbol and union markers
String text = out1.getText('UTF-8')
assert text.contains('"Status"')
assert text.contains('"oneOf"') || text.contains('"anyOf"') || text.contains('"type":[')

// Doc assertions
def json = new groovy.json.JsonSlurper().parse(out1)
assert json.title == 'Order'
assert json.description == 'Order with optional fields and union customer'
assert json.properties.orderId.description == 'Order identifier'
assert json.properties.note.description == 'Optional note'
assert json.properties.discount.description == 'Optional discount'
assert json.properties.customer.anyOf || json.properties.customer.oneOf || json.properties.customer.type instanceof List
