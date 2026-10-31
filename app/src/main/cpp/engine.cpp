// EchoDrumz audio engine: pad playback + sample-accurate 16-step sequencer.
// Audio callback rules: no allocation, no locks, no disk, no JNI.
#include <jni.h>
#include <oboe/Oboe.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstring>
#include <memory>
#include <mutex>
#include <vector>

namespace {

constexpr int kPads = 16;
constexpr int kSteps = 16;
constexpr int kVoices = 32;
constexpr uint32_t kQueueSize = 256;

struct Sample {
    std::vector<float> data;  // interleaved, 1 or 2 channels
    int frames = 0;
    int channels = 1;
    int sampleRate = 44100;
};

struct PadParams {
    std::atomic<float> volume{1.0f};
    std::atomic<float> pitch{0.0f};  // semitones
    std::atomic<float> pan{0.0f};    // -1..1
    std::atomic<bool> gate{false};
    std::atomic<int> choke{0};       // 0 = none
    std::atomic<bool> mute{false};
    std::atomic<Sample*> sample{nullptr};
};

enum EventType { kTrigger = 0, kReleaseGate = 1, kReleaseAll = 2 };
struct Event { int type; int pad; float velocity; };

// Single-consumer (audio thread) queue; producers are serialized by a mutex on the UI side.
class EventQueue {
public:
    bool push(const Event& e) {
        std::lock_guard<std::mutex> lock(producerMutex_);
        uint32_t h = head_.load(std::memory_order_relaxed);
        uint32_t n = (h + 1) % kQueueSize;
        if (n == tail_.load(std::memory_order_acquire)) return false;
        buf_[h] = e;
        head_.store(n, std::memory_order_release);
        return true;
    }
    bool pop(Event& e) {  // audio thread only
        uint32_t t = tail_.load(std::memory_order_relaxed);
        if (t == head_.load(std::memory_order_acquire)) return false;
        e = buf_[t];
        tail_.store((t + 1) % kQueueSize, std::memory_order_release);
        return true;
    }
private:
    Event buf_[kQueueSize];
    std::atomic<uint32_t> head_{0}, tail_{0};
    std::mutex producerMutex_;
};

struct Voice {
    Sample* sample = nullptr;
    double pos = 0.0;
    double inc = 1.0;
    float gainL = 0.f, gainR = 0.f;
    float env = 1.f;
    int pad = -1;
    bool active = false, gate = false, releasing = false;
    uint64_t age = 0;
};

class Engine : public oboe::AudioStreamDataCallback,
               public oboe::AudioStreamErrorCallback,
               public std::enable_shared_from_this<Engine> {
public:
    bool start() {
        std::lock_guard<std::mutex> lock(lifecycle_);
        return startLocked();
    }
    void stop() {
        std::lock_guard<std::mutex> lock(lifecycle_);
        playing_.store(false);
        if (stream_) {
            stream_->stop();
            stream_->close();
            stream_.reset();
        }
    }

    // ---- control surface (UI / JNI threads) ----
    void trigger(int pad, float vel) { queue_.push({kTrigger, pad, vel}); }
    void release(int pad) { queue_.push({kReleaseGate, pad, 0.f}); }
    void setSample(int pad, Sample* ns) {
        Sample* old = pads_[pad].sample.exchange(ns, std::memory_order_acq_rel);
        queue_.push({kReleaseAll, pad, 0.f});  // fade out voices still reading the old data
        bury(old);
    }
    PadParams& pad(int i) { return pads_[i]; }
    void setBpm(float b) { bpm_.store(std::max(20.f, std::min(300.f, b))); }
    void setSwing(float s) { swing_.store(std::max(0.f, std::min(0.45f, s))); }
    void setPlaying(bool p) { playing_.store(p); }
    void setStep(int pad, int step, int vel) { steps_[pad][step].store((uint8_t)std::max(0, std::min(127, vel))); }
    int playhead() const { return playhead_.load(); }

    // ---- oboe callbacks ----
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* audioData, int32_t numFrames) override {
        float* out = static_cast<float*>(audioData);
        std::memset(out, 0, sizeof(float) * 2 * (size_t)numFrames);
        const double sr = (double)sampleRate_.load(std::memory_order_relaxed);
        relStep_ = (float)(1.0 / (0.006 * sr));  // ~6 ms release ramp

        Event e;
        while (queue_.pop(e)) handleEvent(e, sr);

        for (int f = 0; f < numFrames; ++f) {
            tickSequencer(sr);
            float l = 0.f, r = 0.f;
            renderVoices(l, r);
            out[2 * f]     = std::max(-1.f, std::min(1.f, l * 0.8f));
            out[2 * f + 1] = std::max(-1.f, std::min(1.f, r * 0.8f));
        }
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream*, oboe::Result) override {
        std::lock_guard<std::mutex> lock(lifecycle_);
        stream_.reset();
        startLocked();  // e.g. headphones unplugged / route change
    }

private:
    bool startLocked() {
        if (stream_) return true;
        oboe::AudioStreamBuilder b;
        b.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setUsage(oboe::Usage::Game)
            ->setDataCallback(shared_from_this())
            ->setErrorCallback(shared_from_this());
        if (b.openStream(stream_) != oboe::Result::OK) { stream_.reset(); return false; }
        sampleRate_.store(stream_->getSampleRate());
        stream_->setBufferSizeInFrames(stream_->getFramesPerBurst() * 2);
        if (stream_->requestStart() != oboe::Result::OK) {
            stream_->close();
            stream_.reset();
            return false;
        }
        return true;
    }

    void bury(Sample* old) {
        using namespace std::chrono;
        std::lock_guard<std::mutex> lock(graveMutex_);
        auto now = steady_clock::now();
        if (old) grave_.push_back({old, now});
        // Free samples retired >30 s ago (voices on them were faded out within ms).
        for (size_t i = 0; i < grave_.size();) {
            if (now - grave_[i].second > seconds(30)) {
                delete grave_[i].first;
                grave_.erase(grave_.begin() + (long)i);
            } else ++i;
        }
    }

    void handleEvent(const Event& e, double sr) {
        if (e.pad < 0 || e.pad >= kPads) return;
        if (e.type == kTrigger) trigger(e.pad, e.velocity, sr);
        else for (auto& v : voices_)
            if (v.active && v.pad == e.pad && (e.type == kReleaseAll || v.gate)) v.releasing = true;
    }

    void trigger(int pad, float vel, double sr) {
        PadParams& pp = pads_[pad];
        if (pp.mute.load(std::memory_order_relaxed)) return;
        Sample* s = pp.sample.load(std::memory_order_acquire);
        if (!s || s->frames <= 0) return;

        int choke = pp.choke.load(std::memory_order_relaxed);
        if (choke > 0)
            for (auto& v : voices_)
                if (v.active && !v.releasing && pads_[v.pad].choke.load(std::memory_order_relaxed) == choke)
                    v.releasing = true;

        Voice* v = &voices_[0];
        for (auto& c : voices_) {
            if (!c.active) { v = &c; break; }
            if (c.age < v->age) v = &c;  // steal oldest if all busy
        }
        float pan = std::max(-1.f, std::min(1.f, pp.pan.load(std::memory_order_relaxed)));
        float angle = (pan + 1.f) * 0.25f * 3.14159265f;
        float gain = pp.volume.load(std::memory_order_relaxed) * std::max(0.f, std::min(1.f, vel));
        v->sample = s;
        v->pos = 0.0;
        v->inc = std::pow(2.0, pp.pitch.load(std::memory_order_relaxed) / 12.0) * ((double)s->sampleRate / sr);
        v->gainL = std::cos(angle) * 1.4142f * gain;
        v->gainR = std::sin(angle) * 1.4142f * gain;
        v->env = 1.f;
        v->pad = pad;
        v->gate = pp.gate.load(std::memory_order_relaxed);
        v->releasing = false;
        v->age = ++ageCounter_;
        v->active = true;
    }

    void tickSequencer(double sr) {
        if (!playing_.load(std::memory_order_relaxed)) {
            if (seqRunning_) { seqRunning_ = false; playhead_.store(-1); }
            return;
        }
        if (!seqRunning_) {
            seqRunning_ = true; step_ = 0; acc_ = 0.0; stepLen_ = lenFor(0, sr);
            fireStep(0, sr); playhead_.store(0);
            return;
        }
        acc_ += 1.0;
        if (acc_ >= stepLen_) {
            acc_ -= stepLen_;
            step_ = (step_ + 1) % kSteps;
            stepLen_ = lenFor(step_, sr);
            fireStep(step_, sr);
            playhead_.store(step_);
        }
    }

    double lenFor(int s, double sr) const {
        double base = sr * 60.0 / (double)bpm_.load(std::memory_order_relaxed) / 4.0;  // 16th note
        double sw = swing_.load(std::memory_order_relaxed);
        return (s % 2 == 0) ? base * (1.0 + sw) : base * (1.0 - sw);
    }

    void fireStep(int s, double sr) {
        for (int p = 0; p < kPads; ++p) {
            int v = steps_[p][s].load(std::memory_order_relaxed);
            if (v > 0) trigger(p, (float)v / 127.f, sr);
        }
    }

    void renderVoices(float& l, float& r) {
        for (auto& v : voices_) {
            if (!v.active) continue;
            const Sample* s = v.sample;
            int i = (int)v.pos;
            if (i >= s->frames) { v.active = false; continue; }
            int j = std::min(i + 1, s->frames - 1);
            float fr = (float)(v.pos - (double)i);
            const float* d = s->data.data();
            float sl, sr;
            if (s->channels == 1) {
                sl = d[i] + (d[j] - d[i]) * fr;
                sr = sl;
            } else {
                sl = d[2 * i] + (d[2 * j] - d[2 * i]) * fr;
                sr = d[2 * i + 1] + (d[2 * j + 1] - d[2 * i + 1]) * fr;
            }
            l += sl * v.gainL * v.env;
            r += sr * v.gainR * v.env;
            v.pos += v.inc;
            if (v.releasing) {
                v.env -= relStep_;
                if (v.env <= 0.f) v.active = false;
            }
        }
    }

    std::mutex lifecycle_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<int> sampleRate_{48000};

    PadParams pads_[kPads];
    std::atomic<uint8_t> steps_[kPads][kSteps] = {};
    std::atomic<float> bpm_{120.f}, swing_{0.f};
    std::atomic<bool> playing_{false};
    std::atomic<int> playhead_{-1};

    EventQueue queue_;
    Voice voices_[kVoices];
    uint64_t ageCounter_ = 0;
    float relStep_ = 0.01f;

    // Audio-thread-only sequencer state
    bool seqRunning_ = false;
    int step_ = 0;
    double acc_ = 0.0, stepLen_ = 0.0;

    std::mutex graveMutex_;
    std::vector<std::pair<Sample*, std::chrono::steady_clock::time_point>> grave_;
};

Engine& eng() {
    static std::shared_ptr<Engine> e = std::make_shared<Engine>();
    return *e;
}

bool okPad(int p) { return p >= 0 && p < kPads; }

}  // namespace

#define ECHO_JNI(name) Java_com_echodrumz_audio_NativeEngine_##name

extern "C" {

JNIEXPORT jboolean JNICALL ECHO_JNI(start)(JNIEnv*, jobject) { return eng().start() ? JNI_TRUE : JNI_FALSE; }
JNIEXPORT void JNICALL ECHO_JNI(stop)(JNIEnv*, jobject) { eng().stop(); }

JNIEXPORT void JNICALL ECHO_JNI(loadSample)(JNIEnv* env, jobject, jint pad, jfloatArray data, jint channels, jint rate) {
    if (!okPad(pad) || channels < 1 || channels > 2 || rate <= 0) return;
    jsize n = env->GetArrayLength(data);
    auto* s = new Sample();
    s->channels = channels;
    s->sampleRate = rate;
    s->frames = n / channels;
    s->data.resize((size_t)s->frames * (size_t)channels);
    env->GetFloatArrayRegion(data, 0, s->frames * channels, s->data.data());
    eng().setSample(pad, s);
}
JNIEXPORT void JNICALL ECHO_JNI(clearSample)(JNIEnv*, jobject, jint pad) { if (okPad(pad)) eng().setSample(pad, nullptr); }
JNIEXPORT void JNICALL ECHO_JNI(triggerPad)(JNIEnv*, jobject, jint pad, jfloat vel) { if (okPad(pad)) eng().trigger(pad, vel); }
JNIEXPORT void JNICALL ECHO_JNI(releasePad)(JNIEnv*, jobject, jint pad) { if (okPad(pad)) eng().release(pad); }

JNIEXPORT void JNICALL ECHO_JNI(setPadParams)(JNIEnv*, jobject, jint pad, jfloat vol, jfloat pitch, jfloat pan,
                                              jboolean gate, jint choke, jboolean mute) {
    if (!okPad(pad)) return;
    PadParams& p = eng().pad(pad);
    p.volume.store(vol); p.pitch.store(pitch); p.pan.store(pan);
    p.gate.store(gate == JNI_TRUE); p.choke.store(choke); p.mute.store(mute == JNI_TRUE);
}
JNIEXPORT void JNICALL ECHO_JNI(setBpm)(JNIEnv*, jobject, jfloat b) { eng().setBpm(b); }
JNIEXPORT void JNICALL ECHO_JNI(setSwing)(JNIEnv*, jobject, jfloat s) { eng().setSwing(s); }
JNIEXPORT void JNICALL ECHO_JNI(setPlaying)(JNIEnv*, jobject, jboolean p) { eng().setPlaying(p == JNI_TRUE); }
JNIEXPORT void JNICALL ECHO_JNI(setStep)(JNIEnv*, jobject, jint pad, jint step, jint vel) {
    if (okPad(pad) && step >= 0 && step < kSteps) eng().setStep(pad, step, vel);
}
JNIEXPORT jint JNICALL ECHO_JNI(getPlayhead)(JNIEnv*, jobject) { return eng().playhead(); }

}  // extern "C"
