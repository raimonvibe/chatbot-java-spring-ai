# Chatbot Frontend - Next.js

A beautiful, modern chatbot interface built with Next.js 15, React 19, Framer Motion, and Tailwind CSS. Inspired by smooth wave animations and contemporary design principles.

## Features

- **Smooth Wave Animations**: Beautiful SVG wave backgrounds with Framer Motion
- **Real-time Chat**: Seamless integration with Spring Boot backend
- **Responsive Design**: Mobile-first approach with Tailwind CSS
- **TypeScript**: Full type safety throughout the application
- **Quick Replies**: Support for predefined quick response buttons
- **Session Management**: Persistent chat sessions
- **Modern UI**: Glass morphism effects and gradient accents

## Tech Stack

- **Next.js 15** - React framework with App Router
- **React 19** - Latest React features
- **TypeScript** - Type-safe development
- **Framer Motion** - Smooth animations and transitions
- **Tailwind CSS** - Utility-first styling
- **Spring Boot API** - Backend integration

## Getting Started

### Prerequisites

- Node.js 18+ installed
- npm or yarn package manager
- Spring Boot backend running on port 8080

### Installation

1. Install dependencies:
```bash
npm install
```

2. Create environment file:
```bash
cp .env.example .env.local
```

3. Update `.env.local` with your configuration:
```env
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_DEFAULT_CHATBOT_ID=1
```

### Development

Run the development server:

```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

### Build

Build for production:

```bash
npm run build
```

Start production server:

```bash
npm start
```

## Project Structure

```
frontend/
├── app/
│   ├── layout.tsx       # Root layout
│   ├── page.tsx         # Home page with chat
│   └── globals.css      # Global styles
├── components/
│   ├── ChatInterface.tsx    # Main chat component
│   ├── Message.tsx          # Message bubble component
│   └── WaveBackground.tsx   # Animated wave background
├── lib/
│   └── api.ts           # API client functions
├── public/              # Static assets
└── tailwind.config.ts   # Tailwind configuration
```

## API Integration

The frontend connects to the following Spring Boot endpoints:

- `POST /api/chat/{chatbotId}` - Send chat messages
- `GET /api/chatbots/{id}` - Get chatbot details
- `GET /api/chatbots/{id}/quick-replies` - Get quick replies

## Customization

### Colors

Edit `tailwind.config.ts` to customize the color scheme:

```typescript
colors: {
  primary: {
    500: '#0ea5e9',  // Main brand color
    600: '#0284c7',
  },
}
```

### Animations

Modify wave animations in `components/WaveBackground.tsx`:

```typescript
animate={{ x: [-100, 0, -100] }}
transition={{ duration: 20 }}
```

### Chatbot ID

Change the default chatbot in `components/ChatInterface.tsx`:

```typescript
const chatbotId = 1; // Your chatbot ID
```

## Features in Detail

### Wave Background
- Multiple layered SVG waves with independent animations
- Smooth morphing between different wave states
- Floating particle effects for added depth

### Chat Interface
- Glass morphism design with backdrop blur
- Smooth message animations with staggered delays
- Real-time typing indicators
- Auto-scroll to latest messages
- Session persistence

### Message Component
- Distinct styling for user and assistant messages
- Gradient backgrounds for user messages
- Timestamps for each message
- Smooth entrance animations

## Browser Support

- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)

## License

MIT License - See LICENSE file for details

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request
