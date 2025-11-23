# MCP Web Interface

A modern, premium web interface for the MCP Server, built with Vite, React, TypeScript, and TailwindCSS.

## Features
- **Glassmorphism Design**: Sleek, modern UI with gradients and blur effects.
- **Authentication**: Login page with mock authentication flow.
- **Dashboard**: Centralized hub for documentation search.
- **Reranking Toggle**: Switch to enable/disable the advanced reranking service.
- **Responsive Layout**: Sidebar navigation and responsive grid.

## Getting Started

### Prerequisites
- Node.js 18+
- npm

### Installation
```bash
cd modules/web
npm install
```

### Development
Start the development server:
```bash
npm run dev
```
Access the app at `http://localhost:5173`.

### Build
Build for production:
```bash
npm run build
```
The output will be in `dist/`.

## Project Structure
- `src/components`: Reusable UI components (Button, Input, Switch).
- `src/layouts`: Page layouts (DashboardLayout).
- `src/pages`: Application pages (LoginPage, DashboardPage).
- `src/lib`: Utilities (Tailwind merger).
