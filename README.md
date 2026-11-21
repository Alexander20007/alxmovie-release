# Lix-API — versão preparada para Vercel

API Flask para extração de streams M3U8, com cache persistente no Supabase.

## Deploy na Vercel

1. Extrai este ZIP.
2. Envia a pasta `Lix-api-main` para um repositório GitHub.
3. Na Vercel, escolhe **Add New → Project** e importa esse repositório.
4. Mantém as definições de build detetadas automaticamente. Não é necessário configurar Build Command nem Output Directory.
5. Em **Settings → Environment Variables**, adiciona:
   - `SUPABASE_URL` — URL do projeto Supabase.
   - `SUPABASE_SERVICE_KEY` — chave secreta/service role do Supabase.
6. Faz **Deploy**.

A aplicação entra pela função `api/index.py`; o `vercel.json` encaminha os pedidos para a aplicação Flask.

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| GET | `/` | Estado da API |
| GET | `/api/stream/movie/<id>` | Extrai M3U8 de filme |
| GET | `/api/stream/tv/<id>/<s>/<e>` | Extrai M3U8 de série |
| GET | `/api/servers/movie/<id>` | Lista servidores de filme |
| GET | `/api/servers/tv/<id>/<s>/<e>` | Lista servidores de série |
| GET | `/api/proxy?url=...` | Proxy de playlists/segmentos HLS |

Parâmetro opcional: `?servidor=NOME`.

## Supabase

O projeto espera as tabelas `stream_providers` e `stream_cache`, com as colunas usadas em `app/supabase_client.py`. A chave `SUPABASE_SERVICE_KEY` deve ser guardada apenas nas variáveis de ambiente da Vercel; nunca a coloques no código ou num repositório público.

## Limitações importantes da Vercel

A Vercel executa funções serverless, não um servidor Flask permanente. Os endpoints de extração podem funcionar, mas o proxy HLS pode falhar com segmentos grandes, pedidos demorados ou limites de duração/tamanho da resposta da função. Para reprodução HLS estável e tráfego elevado, é preferível manter o proxy num serviço de servidor persistente (por exemplo, Render ou VPS) e usar a Vercel para os endpoints leves.

Os pedidos externos têm timeouts definidos no código. Se a função exceder o limite do plano Vercel, poderá devolver erro de timeout; aumentar `maxDuration` só funciona dentro dos limites do plano.

## Execução local

```bash
pip install -r requirements.txt
# Define SUPABASE_URL e SUPABASE_SERVICE_KEY no ambiente
python -m app.main
```
