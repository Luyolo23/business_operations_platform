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
FROM nginx:1.31-alpine

# Remove default Nginx page and give the unprivileged nginx user the paths it
# needs at runtime (cache, pid). The container no longer runs as root.
RUN rm -rf /usr/share/nginx/html/* \
    && chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx \
    && touch /var/run/nginx.pid \
    && chown nginx:nginx /var/run/nginx.pid

# Copy built assets
COPY --from=builder /app/dist /usr/share/nginx/html

# SPA routing: any path that doesn't match a file falls back to index.html
# so that React Router handles client-side navigation.
COPY nginx.conf /etc/nginx/conf.d/default.conf

USER nginx

# nginx listens on 8080 because an unprivileged user cannot bind 80.
EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
