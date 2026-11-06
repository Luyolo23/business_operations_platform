# ─── Stage 1: build the React app ────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

# Install dependencies first (cached layer unless package.json changes)
COPY package.json package-lock.json ./
RUN npm ci --silent

# Copy source & build
COPY . .
RUN npm run build

# ─── Stage 2: serve with Nginx ────────────────────────────────────────────────
FROM nginx:1.27-alpine

# Remove default Nginx page
RUN rm -rf /usr/share/nginx/html/*

# Copy built assets
COPY --from=builder /app/dist /usr/share/nginx/html

# SPA routing: any path that doesn't match a file falls back to index.html
# so that React Router handles client-side navigation.
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
