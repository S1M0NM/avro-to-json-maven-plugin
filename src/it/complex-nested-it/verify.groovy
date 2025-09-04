File out = new File( basedir, "target/generated-schemas/complex.schema.json" )
assert out.isFile()

def text = out.getText('UTF-8')
assert text.contains('"Currency"')
assert text.contains('"BlobId"')
assert text.contains('"Address"')
assert text.contains('"oneOf"') || text.contains('"anyOf"') || text.contains('"type":[')
