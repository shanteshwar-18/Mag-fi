import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { motion, AnimatePresence } from 'motion/react';
import {
  ChevronLeft,
  Crosshair,
  RefreshCw,
  ZoomIn,
  ZoomOut,
  Wifi,
  Radio,
  Navigation2,
  MapPin,
} from 'lucide-react';
import { Button } from '../components/ui/button';

export default function LiveLocalization() {
  const navigate = useNavigate();
  const [position, setPosition] = useState({ x: 150, y: 200 });
  const [heading, setHeading] = useState(45);
  const [accuracy, setAccuracy] = useState(2.3);
  const [magneticStrength, setMagneticStrength] = useState(78);
  const [wifiSignal, setWifiSignal] = useState(85);
  const [isTracking, setIsTracking] = useState(true);
  const [showBottomSheet, setShowBottomSheet] = useState(false);

  // Simulate live positioning
  useEffect(() => {
    if (!isTracking) return;

    const interval = setInterval(() => {
      setPosition((prev) => ({
        x: prev.x + (Math.random() - 0.5) * 2,
        y: prev.y + (Math.random() - 0.5) * 2,
      }));
      setHeading((prev) => (prev + (Math.random() - 0.5) * 5) % 360);
      setAccuracy(2 + Math.random() * 0.5);
      setMagneticStrength(75 + Math.random() * 10);
      setWifiSignal(80 + Math.random() * 15);
    }, 1000);

    return () => clearInterval(interval);
  }, [isTracking]);

  const recenter = () => {
    setPosition({ x: 150, y: 200 });
  };

  const recalibrate = () => {
    setAccuracy(1.5);
    // Simulate calibration
  };

  return (
    <div className="min-h-screen bg-slate-950 relative overflow-hidden">
      {/* Top Bar */}
      <div className="absolute top-0 left-0 right-0 z-30 bg-gradient-to-b from-slate-900/95 to-slate-900/0 backdrop-blur-md border-b border-cyan-500/20">
        <div className="p-4 flex items-center justify-between">
          <button
            onClick={() => navigate('/home')}
            className="p-2 rounded-xl bg-slate-800/80 border border-cyan-500/30 hover:bg-slate-700/80 transition-colors"
          >
            <ChevronLeft className="w-6 h-6 text-cyan-400" />
          </button>
          <div className="flex-1 ml-4">
            <h1 className="text-white font-semibold">Engineering Block A</h1>
            <p className="text-cyan-400 text-sm">Floor 3 • Accuracy: {accuracy.toFixed(1)}m</p>
          </div>
          <div
            className={`px-3 py-1.5 rounded-lg border ${
              isTracking
                ? 'bg-green-500/20 border-green-500/40 text-green-400'
                : 'bg-red-500/20 border-red-500/40 text-red-400'
            }`}
          >
            <p className="text-xs font-medium">{isTracking ? 'Tracking...' : 'Paused'}</p>
          </div>
        </div>
      </div>

      {/* Indoor Map */}
      <div className="absolute inset-0 bg-slate-900">
        <svg className="w-full h-full" viewBox="0 0 400 600">
          {/* Floor Plan Background */}
          <rect width="400" height="600" fill="#0f172a" />

          {/* Grid Pattern */}
          <pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse">
            <path d="M 20 0 L 0 0 0 20" fill="none" stroke="#1e3a8a" strokeWidth="0.5" opacity="0.3" />
          </pattern>
          <rect width="400" height="600" fill="url(#grid)" />

          {/* Rooms */}
          <g opacity="0.6">
            {/* Lab 301 */}
            <rect x="50" y="100" width="120" height="80" fill="#1e293b" stroke="#0ea5e9" strokeWidth="2" />
            <text x="110" y="145" fill="#0ea5e9" fontSize="10" textAnchor="middle">
              Lab 301
            </text>

            {/* Lab 302 */}
            <rect x="230" y="100" width="120" height="80" fill="#1e293b" stroke="#0ea5e9" strokeWidth="2" />
            <text x="290" y="145" fill="#0ea5e9" fontSize="10" textAnchor="middle">
              Lab 302
            </text>

            {/* Corridor */}
            <rect x="170" y="100" width="60" height="400" fill="#0f172a" stroke="#06b6d4" strokeWidth="1.5" />
            <text x="200" y="300" fill="#06b6d4" fontSize="9" textAnchor="middle">
              CORRIDOR
            </text>

            {/* Classroom 303 */}
            <rect x="50" y="200" width="120" height="100" fill="#1e293b" stroke="#0ea5e9" strokeWidth="2" />
            <text x="110" y="255" fill="#0ea5e9" fontSize="10" textAnchor="middle">
              Room 303
            </text>

            {/* Classroom 304 */}
            <rect x="230" y="200" width="120" height="100" fill="#1e293b" stroke="#0ea5e9" strokeWidth="2" />
            <text x="290" y="255" fill="#0ea5e9" fontSize="10" textAnchor="middle">
              Room 304
            </text>

            {/* Washroom */}
            <rect x="50" y="320" width="60" height="60" fill="#1e293b" stroke="#0ea5e9" strokeWidth="2" />
            <text x="80" y="355" fill="#0ea5e9" fontSize="8" textAnchor="middle">
              WC
            </text>

            {/* Office */}
            <rect x="230" y="320" width="120" height="80" fill="#1e293b" stroke="#0ea5e9" strokeWidth="2" />
            <text x="290" y="365" fill="#0ea5e9" fontSize="10" textAnchor="middle">
              Office
            </text>
          </g>

          {/* Wi-Fi Zone Indicator */}
          <circle cx={position.x} cy={position.y} r="40" fill="#06b6d4" opacity="0.1" />
          <circle cx={position.x} cy={position.y} r="25" fill="#06b6d4" opacity="0.15" />

          {/* Navigation Path (if exists) */}
          <path
            d={`M ${position.x} ${position.y} L 290 355`}
            stroke="#3b82f6"
            strokeWidth="3"
            strokeDasharray="5,5"
            fill="none"
            opacity="0.5"
          />

          {/* Blue Dot (User Position) */}
          <g>
            {/* Pulsing outer ring */}
            <motion.circle
              cx={position.x}
              cy={position.y}
              r="15"
              fill="#3b82f6"
              opacity="0.3"
              animate={{
                r: [15, 20, 15],
                opacity: [0.3, 0, 0.3],
              }}
              transition={{
                duration: 2,
                repeat: Infinity,
                ease: 'easeInOut',
              }}
            />

            {/* Main blue dot */}
            <circle cx={position.x} cy={position.y} r="8" fill="#3b82f6" stroke="#fff" strokeWidth="2" />

            {/* Heading direction arrow */}
            <motion.path
              d={`M ${position.x} ${position.y - 8} L ${position.x - 4} ${position.y - 15} L ${position.x + 4} ${
                position.y - 15
              } Z`}
              fill="#fff"
              animate={{
                rotate: heading,
              }}
              style={{
                transformOrigin: `${position.x}px ${position.y}px`,
              }}
            />

            {/* Accuracy circle */}
            <circle
              cx={position.x}
              cy={position.y}
              r={accuracy * 10}
              fill="none"
              stroke="#3b82f6"
              strokeWidth="1"
              strokeDasharray="3,3"
              opacity="0.5"
            />
          </g>

          {/* Coordinates Display */}
          <text x={position.x} y={position.y + 30} fill="#06b6d4" fontSize="9" textAnchor="middle">
            ({position.x.toFixed(1)}, {position.y.toFixed(1)})
          </text>
        </svg>
      </div>

      {/* Floating Action Buttons */}
      <div className="absolute right-4 top-1/2 -translate-y-1/2 z-20 space-y-3">
        <motion.button
          whileTap={{ scale: 0.95 }}
          onClick={recenter}
          className="p-4 rounded-full bg-slate-800/90 border border-cyan-500/40 shadow-lg shadow-cyan-500/20 hover:bg-slate-700/90 transition-colors"
        >
          <Crosshair className="w-6 h-6 text-cyan-400" />
        </motion.button>

        <motion.button
          whileTap={{ scale: 0.95 }}
          onClick={recalibrate}
          className="p-4 rounded-full bg-slate-800/90 border border-cyan-500/40 shadow-lg shadow-cyan-500/20 hover:bg-slate-700/90 transition-colors"
        >
          <RefreshCw className="w-6 h-6 text-cyan-400" />
        </motion.button>

        <motion.button
          whileTap={{ scale: 0.95 }}
          className="p-4 rounded-full bg-slate-800/90 border border-cyan-500/40 shadow-lg shadow-cyan-500/20 hover:bg-slate-700/90 transition-colors"
        >
          <ZoomIn className="w-6 h-6 text-cyan-400" />
        </motion.button>

        <motion.button
          whileTap={{ scale: 0.95 }}
          className="p-4 rounded-full bg-slate-800/90 border border-cyan-500/40 shadow-lg shadow-cyan-500/20 hover:bg-slate-700/90 transition-colors"
        >
          <ZoomOut className="w-6 h-6 text-cyan-400" />
        </motion.button>
      </div>

      {/* Signal Indicators */}
      <div className="absolute top-24 left-4 z-20 space-y-3">
        <motion.div
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          className="bg-slate-800/90 backdrop-blur-sm border border-cyan-500/40 rounded-xl p-3 shadow-lg"
        >
          <div className="flex items-center gap-2">
            <Radio className="w-5 h-5 text-cyan-400" />
            <div>
              <p className="text-white text-xs font-medium">Magnetic</p>
              <div className="flex items-center gap-2 mt-1">
                <div className="flex-1 h-1.5 bg-slate-700 rounded-full overflow-hidden w-16">
                  <motion.div
                    className="h-full bg-gradient-to-r from-cyan-500 to-cyan-400"
                    animate={{ width: `${magneticStrength}%` }}
                  />
                </div>
                <span className="text-cyan-400 text-xs">{magneticStrength}%</span>
              </div>
            </div>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: 0.1 }}
          className="bg-slate-800/90 backdrop-blur-sm border border-cyan-500/40 rounded-xl p-3 shadow-lg"
        >
          <div className="flex items-center gap-2">
            <Wifi className="w-5 h-5 text-cyan-400" />
            <div>
              <p className="text-white text-xs font-medium">Wi-Fi Zone</p>
              <div className="flex items-center gap-2 mt-1">
                <div className="flex-1 h-1.5 bg-slate-700 rounded-full overflow-hidden w-16">
                  <motion.div
                    className="h-full bg-gradient-to-r from-blue-500 to-blue-400"
                    animate={{ width: `${wifiSignal}%` }}
                  />
                </div>
                <span className="text-blue-400 text-xs">{wifiSignal}%</span>
              </div>
            </div>
          </div>
        </motion.div>
      </div>

      {/* Bottom Sheet Toggle */}
      <motion.button
        onClick={() => setShowBottomSheet(!showBottomSheet)}
        className="absolute bottom-0 left-0 right-0 z-20 bg-slate-800/95 backdrop-blur-sm border-t border-cyan-500/30 p-4"
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-1 h-12 bg-cyan-500 rounded-full" />
            <div>
              <p className="text-white font-medium">Sensor Readings</p>
              <p className="text-cyan-400 text-sm">Confidence: 94%</p>
            </div>
          </div>
          <motion.div animate={{ rotate: showBottomSheet ? 180 : 0 }}>
            <Navigation2 className="w-6 h-6 text-cyan-400" />
          </motion.div>
        </div>
      </motion.button>

      {/* Bottom Sheet */}
      <AnimatePresence>
        {showBottomSheet && (
          <motion.div
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ type: 'spring', damping: 30, stiffness: 300 }}
            className="absolute bottom-0 left-0 right-0 z-30 bg-slate-900/98 backdrop-blur-lg border-t border-cyan-500/30 rounded-t-3xl shadow-2xl"
            style={{ maxHeight: '50vh' }}
          >
            <div className="p-6 space-y-4 overflow-auto" style={{ maxHeight: '50vh' }}>
              <div className="flex items-center justify-center">
                <div className="w-12 h-1 bg-slate-700 rounded-full" />
              </div>

              <h3 className="text-white font-semibold text-lg">Live Sensor Data</h3>

              <div className="grid grid-cols-2 gap-4">
                <div className="bg-slate-800/50 border border-cyan-500/30 rounded-xl p-4">
                  <p className="text-cyan-400 text-xs mb-2">Heading</p>
                  <p className="text-white text-2xl font-bold">{heading.toFixed(0)}°</p>
                </div>

                <div className="bg-slate-800/50 border border-cyan-500/30 rounded-xl p-4">
                  <p className="text-cyan-400 text-xs mb-2">Speed</p>
                  <p className="text-white text-2xl font-bold">1.2 m/s</p>
                </div>

                <div className="bg-slate-800/50 border border-cyan-500/30 rounded-xl p-4">
                  <p className="text-cyan-400 text-xs mb-2">Altitude</p>
                  <p className="text-white text-2xl font-bold">12.4 m</p>
                </div>

                <div className="bg-slate-800/50 border border-cyan-500/30 rounded-xl p-4">
                  <p className="text-cyan-400 text-xs mb-2">Confidence</p>
                  <p className="text-white text-2xl font-bold">94%</p>
                </div>
              </div>

              <div className="bg-gradient-to-r from-cyan-900/30 to-blue-900/30 border border-cyan-500/30 rounded-xl p-4">
                <p className="text-cyan-300 text-sm font-medium mb-2">Current Fingerprint Match</p>
                <p className="text-white text-xs">Zone: Corridor-3B</p>
                <p className="text-cyan-400 text-xs mt-1">AP Count: 8 • RSSI Avg: -45 dBm</p>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
