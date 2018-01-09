# FactExtractiCloud

FactExtractCloud is a simplified version of FactExtract that extracts annotations and writes them to Cloudant databases on Bluemix rather than to a relational database. See the FactExtract page for details of the motivation for this type of annotator. FactExtract supports fine grained control of what get extracted and how by the use of a configuration table in the target database, whereas FactExtractCloud takes a much simpler approach in that there's no configuration at all. An annotation gets persisted if it includes a user defined trigger feature and doesn't if the feature isn't present. The name of the feature used as a trigger is user defined and the default is "persist". 


