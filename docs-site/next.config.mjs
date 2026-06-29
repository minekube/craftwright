import { createMDX } from 'fumadocs-mdx/next';

const withMDX = createMDX();

const nextConfig = {
  output: 'export',
  trailingSlash: true,
  reactStrictMode: true,
  images: {
    unoptimized: true,
  },
};

export default withMDX(nextConfig);
