import { useNavigate, useLocation } from 'react-router';
import { motion } from 'motion/react';
import {
  ChevronLeft,
  Navigation,
  MapPin,
  Footprints,
  Clock,
  TrendingUp,
  Flag,
} from 'lucide-react';
import { Button } from '../components/ui/button';

export default function RoutePreview() {
  const navigate = useNavigate();
  const location = useLocation();
  const destination = location.state?.destination || 'Computer Lab 301';

  const routeInfo = {
    distance: '48m',
    steps: 65,
    estimatedTime: '1 min',
    floors: 'Same floor',
  };

  return (
    <div className="min-h-screen bg-slate-950 relative overflow-hidden">
      {/* Header */}
      <div className="absolute top-0 left-0 right-0 z-20 bg-gradient-to-b from-slate-900/95 to-slate-900/0 backdrop-blur-md border-b border-cyan-500/20">
        <div className="p-4 flex items-center justify-between">
          <button
            onClick={() => navigate('/search')}
            className="p-2 rounded-xl bg-slate-800/80 border border-cyan-500/30 hover:bg-slate-700/80 transition-colors"
          >
            <ChevronLeft className="w-6 h-6 text-cyan-400" />
          </button>
          <h1 className="text-white font-semibold flex-1 ml-4">Route Preview</h1>
        </div>
      </div>

      {/* Map Preview */}
      <div className="absolute inset-0 bg-slate-900">
        <svg className="w-full h-full" viewBox="0 0 400 700">
          {/* Floor Plan Background */}
          <rect width="400" height="700" fill="#0f172a" />

          {/* Grid Pattern */}
          <pattern id="grid-route" width="20" height="20" patternUnits="userSpaceOnUse">
            <path d="M 20 0 L 0 0 0 20" fill="none" stroke="#1e3a8a" strokeWidth="0.5" opacity="0.2" />
          </pattern>
          <rect width="400" height="700" fill="url(#grid-route)" />

          {/* Rooms (dimmed) */}
          <g opacity="0.3">
            <rect x="50" y="150" width="120" height="80" fill="#1e293b" stroke="#475569" strokeWidth="1" />
            <rect x="230" y="150" width="120" height="80" fill="#1e293b" stroke="#475569" strokeWidth="1" />
            <rect x="170" y="150" width="60" height="350" fill="#0f172a" stroke="#475569" strokeWidth="1" />
            <rect x="50" y="250" width="120" height="100" fill="#1e293b" stroke="#475569" strokeWidth="1" />
            <rect x="230" y="250" width="120" height="100" fill="#1e293b" stroke="#475569" strokeWidth="1" />
            <rect x="50" y="370" width="60" height="60" fill="#1e293b" stroke="#475569" strokeWidth="1" />
            <rect x="230" y="370" width="120" height="80" fill="#1e293b" stroke="#475569" strokeWidth="1" />
          </g>

          {/* Destination Room (highlighted) */}
          <rect x="50" y="150" width="120" height="80" fill="#0ea5e9" fillOpacity="0.2" stroke="#0ea5e9" strokeWidth="2" />
          <text x="110" y="195" fill="#0ea5e9" fontSize="11" textAnchor="middle" fontWeight="600">
            Lab 301
          </text>

          {/* Animated Route Path */}
          <defs>
            <linearGradient id="routeGradient" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.8" />
              <stop offset="100%" stopColor="#06b6d4" stopOpacity="0.8" />
            </linearGradient>
          </defs>

          {/* Main Route */}
          <motion.path
            d="M 200 400 L 200 320 L 200 240 L 150 240 L 110 240 L 110 190"
            stroke="url(#routeGradient)"
            strokeWidth="5"
            fill="none"
            strokeLinecap="round"
            strokeLinejoin="round"
            initial={{ pathLength: 0 }}
            animate={{ pathLength: 1 }}
            transition={{ duration: 1.5, ease: 'easeInOut' }}
          />

          {/* Animated Dots on Path */}
          <motion.circle
            cx="200"
            cy="400"
            r="4"
            fill="#3b82f6"
            animate={{
              cx: [200, 200, 200, 150, 110, 110],
              cy: [400, 320, 240, 240, 240, 190],
            }}
            transition={{
              duration: 2,
              repeat: Infinity,
              ease: 'linear',
            }}
          />

          {/* Start Point */}
          <g>
            <circle cx="200" cy="400" r="12" fill="#10b981" stroke="#fff" strokeWidth="2" />
            <MapPin className="w-5 h-5 text-white" x="192.5" y="392.5" />
          </g>

          {/* End Point (Destination) */}
          <g>
            <motion.circle
              cx="110"
              cy="190"
              r="15"
              fill="#ef4444"
              opacity="0.3"
              animate={{
                r: [15, 20, 15],
                opacity: [0.3, 0, 0.3],
              }}
              transition={{
                duration: 1.5,
                repeat: Infinity,
                ease: 'easeInOut',
              }}
            />
            <circle cx="110" cy="190" r="12" fill="#ef4444" stroke="#fff" strokeWidth="2" />
            <Flag className="w-5 h-5 text-white" x="102.5" y="182.5" />
          </g>

          {/* Labels */}
          <text x="200" y="425" fill="#10b981" fontSize="10" textAnchor="middle" fontWeight="600">
            Your Location
          </text>
          <text x="110" y="170" fill="#ef4444" fontSize="10" textAnchor="middle" fontWeight="600">
            Destination
          </text>

          {/* Direction Indicators */}
          <g opacity="0.6">
            <motion.path
              d="M 200 360 L 205 350 L 200 352 L 195 350 Z"
              fill="#06b6d4"
              animate={{ y: [0, -5, 0] }}
              transition={{ duration: 1, repeat: Infinity }}
            />
            <motion.path
              d="M 200 280 L 205 270 L 200 272 L 195 270 Z"
              fill="#06b6d4"
              animate={{ y: [0, -5, 0] }}
              transition={{ duration: 1, repeat: Infinity, delay: 0.3 }}
            />
            <motion.path
              d="M 175 240 L 165 235 L 167 240 L 165 245 Z"
              fill="#06b6d4"
              animate={{ x: [0, -5, 0] }}
              transition={{ duration: 1, repeat: Infinity, delay: 0.6 }}
            />
          </g>
        </svg>
      </div>

      {/* Bottom Info Card */}
      <div className="absolute bottom-0 left-0 right-0 z-20">
        <motion.div
          initial={{ y: 100 }}
          animate={{ y: 0 }}
          transition={{ type: 'spring', damping: 30, stiffness: 300 }}
          className="bg-gradient-to-b from-slate-900/95 to-slate-900/98 backdrop-blur-lg border-t border-cyan-500/30 rounded-t-3xl shadow-2xl p-6"
        >
          {/* Drag Handle */}
          <div className="flex items-center justify-center mb-4">
            <div className="w-12 h-1 bg-slate-700 rounded-full" />
          </div>

          {/* Destination Info */}
          <div className="mb-6">
            <div className="flex items-center gap-3 mb-2">
              <div className="p-2 bg-red-500/20 rounded-xl">
                <Flag className="w-6 h-6 text-red-400" />
              </div>
              <div>
                <p className="text-cyan-400 text-xs">Destination</p>
                <h2 className="text-white text-xl font-semibold">{destination}</h2>
              </div>
            </div>
            <p className="text-slate-400 text-sm ml-14">Engineering Block A • Floor 3</p>
          </div>

          {/* Route Statistics */}
          <div className="grid grid-cols-2 gap-4 mb-6">
            <div className="bg-slate-800/50 border border-cyan-500/30 rounded-2xl p-4">
              <div className="flex items-center gap-2 mb-2">
                <TrendingUp className="w-4 h-4 text-cyan-400" />
                <p className="text-cyan-400 text-xs">Distance</p>
              </div>
              <p className="text-white text-2xl font-bold">{routeInfo.distance}</p>
            </div>

            <div className="bg-slate-800/50 border border-cyan-500/30 rounded-2xl p-4">
              <div className="flex items-center gap-2 mb-2">
                <Clock className="w-4 h-4 text-cyan-400" />
                <p className="text-cyan-400 text-xs">Time</p>
              </div>
              <p className="text-white text-2xl font-bold">{routeInfo.estimatedTime}</p>
            </div>

            <div className="bg-slate-800/50 border border-cyan-500/30 rounded-2xl p-4">
              <div className="flex items-center gap-2 mb-2">
                <Footprints className="w-4 h-4 text-cyan-400" />
                <p className="text-cyan-400 text-xs">Steps</p>
              </div>
              <p className="text-white text-2xl font-bold">{routeInfo.steps}</p>
            </div>

            <div className="bg-slate-800/50 border border-cyan-500/30 rounded-2xl p-4">
              <div className="flex items-center gap-2 mb-2">
                <TrendingUp className="w-4 h-4 text-cyan-400" />
                <p className="text-cyan-400 text-xs">Floors</p>
              </div>
              <p className="text-white text-lg font-bold">{routeInfo.floors}</p>
            </div>
          </div>

          {/* Turn-by-Turn Preview */}
          <div className="bg-gradient-to-r from-cyan-900/20 to-blue-900/20 border border-cyan-500/30 rounded-2xl p-4 mb-6">
            <p className="text-cyan-300 text-sm font-medium mb-3">Turn-by-Turn Instructions</p>
            <div className="space-y-2">
              <div className="flex items-start gap-3">
                <div className="w-6 h-6 rounded-full bg-cyan-500/20 border border-cyan-500/40 flex items-center justify-center flex-shrink-0 mt-0.5">
                  <span className="text-cyan-400 text-xs font-bold">1</span>
                </div>
                <p className="text-slate-300 text-sm">Head north along the corridor</p>
              </div>
              <div className="flex items-start gap-3">
                <div className="w-6 h-6 rounded-full bg-cyan-500/20 border border-cyan-500/40 flex items-center justify-center flex-shrink-0 mt-0.5">
                  <span className="text-cyan-400 text-xs font-bold">2</span>
                </div>
                <p className="text-slate-300 text-sm">Turn left at the intersection</p>
              </div>
              <div className="flex items-start gap-3">
                <div className="w-6 h-6 rounded-full bg-cyan-500/20 border border-cyan-500/40 flex items-center justify-center flex-shrink-0 mt-0.5">
                  <span className="text-cyan-400 text-xs font-bold">3</span>
                </div>
                <p className="text-slate-300 text-sm">Destination on your left</p>
              </div>
            </div>
          </div>

          {/* Start Navigation Button */}
          <Button
            onClick={() => navigate('/localization')}
            className="w-full bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white py-6 rounded-2xl shadow-lg shadow-cyan-500/30 transition-all hover:shadow-cyan-500/50"
          >
            <Navigation className="w-5 h-5 mr-2" />
            Start Navigation
          </Button>
        </motion.div>
      </div>
    </div>
  );
}
