const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const supabase = require('../config/supabase');

const jwtSecret = process.env.JWT_SECRET || 'fallback_secret_key';

const register = async (req, res, next) => {
  try {
    const { email, password, display_name } = req.body;

    if (!email || !password) {
      return res.status(400).json({ success: false, message: 'Email and password are required' });
    }

    // Check existing user
    const { data: existingUser } = await supabase.from('users').select('*').eq('email', email.toLowerCase()).single();
    if (existingUser) {
      return res.status(400).json({ success: false, message: 'User with this email already exists' });
    }

    const salt = await bcrypt.genSalt(10);
    const passwordHash = await bcrypt.hash(password, salt);

    // Insert user
    const { data: newUser, error: userError } = await supabase
      .from('users')
      .insert([{ email: email.toLowerCase(), password_hash: passwordHash, role: 'USER', status: 'ACTIVE' }])
      .select()
      .single();

    if (userError) throw userError;

    // Insert profile
    await supabase.from('profiles').insert([{ user_id: newUser.id, display_name: display_name || email.split('@')[0] }]);

    // Insert baseline cycle settings
    await supabase.from('cycle_settings').insert([{ user_id: newUser.id, average_cycle_length: 28, average_period_length: 5 }]);

    // Generate token
    const token = jwt.sign({ id: newUser.id, email: newUser.email, role: newUser.role }, jwtSecret, { expiresIn: '7d' });

    res.status(201).json({
      success: true,
      message: 'User registered successfully',
      token,
      user: {
        id: newUser.id,
        email: newUser.email,
        role: newUser.role,
        display_name: display_name || email.split('@')[0]
      }
    });
  } catch (err) {
    next(err);
  }
};

const login = async (req, res, next) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ success: false, message: 'Email and password are required' });
    }

    const { data: user, error } = await supabase
      .from('users')
      .select('*, profiles(display_name, profile_image)')
      .eq('email', email.toLowerCase())
      .single();

    if (error || !user) {
      return res.status(401).json({ success: false, message: 'Invalid credentials' });
    }

    if (user.status !== 'ACTIVE') {
      return res.status(403).json({ success: false, message: 'Account is disabled' });
    }

    const isMatch = await bcrypt.compare(password, user.password_hash);
    if (!isMatch) {
      return res.status(401).json({ success: false, message: 'Invalid credentials' });
    }

    const token = jwt.sign({ id: user.id, email: user.email, role: user.role }, jwtSecret, { expiresIn: '7d' });

    const profile = Array.isArray(user.profiles) ? user.profiles[0] : user.profiles;

    res.json({
      success: true,
      message: 'Login successful',
      token,
      user: {
        id: user.id,
        email: user.email,
        role: user.role,
        display_name: profile?.display_name || user.email.split('@')[0],
        profile_image: profile?.profile_image || null
      }
    });
  } catch (err) {
    next(err);
  }
};

const me = async (req, res, next) => {
  try {
    const { data: user, error } = await supabase
      .from('users')
      .select('id, email, role, status, profiles(display_name, profile_image), cycle_settings(*)')
      .eq('id', req.user.id)
      .single();

    if (error || !user) return res.status(444).json({ success: false, message: 'User not found' });

    res.json({ success: true, user });
  } catch (err) {
    next(err);
  }
};

const logout = async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (userId) {
      await supabase.from('user_sessions').delete().eq('user_id', userId);
      await supabase.from('audit_logs').insert([{
        actor_user_id: userId,
        action: 'USER_LOGOUT',
        entity_type: 'AUTH',
        entity_id: userId,
        metadata: { timestamp: new Date() }
      }]);
    }
    res.json({ success: true, message: 'Logged out successfully' });
  } catch (err) {
    next(err);
  }
};

const refresh = async (req, res, next) => {
  try {
    const { token } = req.body;
    if (!token) {
      return res.status(400).json({ success: false, message: 'Token is required' });
    }

    let decoded;
    try {
      decoded = jwt.verify(token, jwtSecret, { ignoreExpiration: true });
    } catch (err) {
      return res.status(401).json({ success: false, message: 'Invalid token' });
    }

    const { data: user, error } = await supabase
      .from('users')
      .select('id, email, role, status')
      .eq('id', decoded.id)
      .single();

    if (error || !user || user.status !== 'ACTIVE') {
      return res.status(403).json({ success: false, message: 'User account not active or not found' });
    }

    const newToken = jwt.sign(
      { id: user.id, email: user.email, role: user.role },
      jwtSecret,
      { expiresIn: '7d' }
    );

    res.json({
      success: true,
      token: newToken,
      user: {
        id: user.id,
        email: user.email,
        role: user.role
      }
    });
  } catch (err) {
    next(err);
  }
};

module.exports = {
  register,
  login,
  logout,
  refresh,
  me
};
