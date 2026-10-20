FROM node:22-alpine
WORKDIR /app

COPY package.json server.js ./
COPY data ./data

EXPOSE 8085 8086 8087 8088

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8085}/mock-store/health || exit 1

CMD ["node", "server.js"]
