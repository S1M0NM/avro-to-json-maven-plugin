File out = new File( basedir, "target/generated-schemas/complex.schema.json" )
assert out.isFile()

def text = out.getText('UTF-8')
assert text.contains('"Currency"')
assert text.contains('"BlobId"')
assert text.contains('"Address"')
assert text.contains('"oneOf"') || text.contains('"anyOf"') || text.contains('"type":[')

// Doc assertions
def json = new groovy.json.JsonSlurper().parse(out)
assert json.title == 'Invoice'
assert json.description == 'An invoice with nested records and unions'
assert json.properties.invoiceId.description == 'Invoice identifier'
assert json.properties.createdAt.description == 'Creation timestamp'
assert json.properties.currency.description == 'Currency code'
assert json.properties.amounts.description == 'Amounts by category'
assert json.properties.attachments.description == 'Binary attachment ids'
assert json.properties.customer.properties.id.description == 'Customer id'
assert json.properties.customer.properties.name.description == 'Customer name'
assert json.properties.customer.properties.addresses.items.properties.street.description == 'Street name'
