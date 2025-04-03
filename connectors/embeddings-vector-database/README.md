# Camunda Embeddings Vector Database Connector

## Embed

### PGVector

`docker run --name pgvector-instance -v ./mount:/var/lib/postgresql/data -e PGDATA=/var/lib/postgresql/data/pgdata -e POSTGRES_PASSWORD=mypassword -p 5432:5432 -d pgvector/pgvector:pg17`

### ElasticSearch

`docker run -d --name elasticsearch-rag -p 9201:9200 -p 9301:9300 -e ELASTIC_PASSWORD=mypassword -e "discovery.type=single-node" -e "xpack.security.enabled=false" elasticsearch:8.16.6`