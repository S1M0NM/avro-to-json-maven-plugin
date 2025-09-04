File out1 = new File( basedir, "target/generated-schemas/optional-union.schema.json" )
assert out1.isFile()

// Basic sanity: file contains Status enum symbol and union markers
String text = out1.getText('UTF-8')
assert text.contains('"Status"')
assert text.contains('"oneOf"') || text.contains('"anyOf"') || text.contains('"type":[')
