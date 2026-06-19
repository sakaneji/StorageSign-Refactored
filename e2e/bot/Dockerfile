FROM node:22-alpine

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts
COPY run-e2e.js ./

CMD ["node", "run-e2e.js"]
