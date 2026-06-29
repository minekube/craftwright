import { createMDX } from 'fumadocs-mdx/next';

const withMDX = createMDX();
const basePath = process.env.CRAFTLESS_DOCS_BASE_PATH;

const nextConfig = {
  output: 'export',
  trailingSlash: true,
  reactStrictMode: true,
  ...(basePath ? { basePath } : {}),
  images: {
    unoptimized: true,
  },
};

export default withMDX(nextConfig);
