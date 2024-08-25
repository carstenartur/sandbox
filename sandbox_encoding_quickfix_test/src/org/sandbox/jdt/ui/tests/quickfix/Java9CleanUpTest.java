package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava9;

public class Java9CleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava9();

	enum ExplicitEncodingPatterns {

//		CHARSET("""
//				package test1;
//
//				import java.io.ByteArrayOutputStream;
//				import java.io.InputStreamReader;
//				import java.io.FileInputStream;
//				import java.io.FileReader;
//				import java.io.Reader;
//				import java.io.FileNotFoundException;
//
//				public class E1 {
//				    void method(String filename) {
//				        Charset cs1= Charset.forName("UTF-8");
//				        Charset cs2= Charset.forName("UTF-16");
//				        Charset cs3= Charset.forName("UTF-16BE");
//				        Charset cs4= Charset.forName("UTF-16LE");
//				        Charset cs5= Charset.forName("ISO-8859-1");
//				        Charset cs6= Charset.forName("US-ASCII");
//				        String result= cs1.toString();
//				       }
//				    }
//				}
//				""",
//
//					"""
//						package test1;
//
//						import java.io.ByteArrayOutputStream;
//						import java.io.InputStreamReader;
//						import java.io.FileInputStream;
//						import java.io.FileReader;
//						import java.io.Reader;
//						import java.nio.charset.Charset;
//						import java.io.FileNotFoundException;
//
//						public class E1 {
//						    void method(String filename) {
//						        Charset cs1= StandardCharsets.UTF_8;
//						        Charset cs2= StandardCharsets.UTF_16;
//						        Charset cs3= StandardCharsets.UTF_16BE;
//						        Charset cs4= StandardCharsets.UTF_16LE;
//						        Charset cs5= StandardCharsets.ISO_8859_1;
//						        Charset cs6= StandardCharsets.US_ASCII;
//						        String result= cs.toString();
//						       }
//						    }
//						}
//						"""),
		BYTEARRAYOUTSTREAM("""
			package test1;

			import java.io.ByteArrayOutputStream;
			import java.io.InputStreamReader;
			import java.io.FileInputStream;
			import java.io.FileReader;
			import java.io.Reader;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        ByteArrayOutputStream ba=new ByteArrayOutputStream();
			        String result=ba.toString();
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.ByteArrayOutputStream;
					import java.io.InputStreamReader;
					import java.io.FileInputStream;
					import java.io.FileReader;
					import java.io.Reader;
					import java.nio.charset.Charset;
					import java.io.FileNotFoundException;

					public class E1 {
					    void method(String filename) {
					        ByteArrayOutputStream ba=new ByteArrayOutputStream();
					        String result=ba.toString(Charset.defaultCharset().displayName());
					       }
					    }
					}
					"""),
		FILEREADER("""
			package test1;

			import java.io.InputStreamReader;
			import java.io.FileInputStream;
			import java.io.FileReader;
			import java.io.Reader;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        try {
			            Reader is=new FileReader(filename);
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.InputStreamReader;
					import java.io.FileInputStream;
					import java.io.FileReader;
					import java.io.Reader;
					import java.nio.charset.Charset;
					import java.io.FileNotFoundException;

					public class E1 {
					    void method(String filename) {
					        try {
					            Reader is=new InputStreamReader(new FileInputStream(filename), Charset.defaultCharset());
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					       }
					    }
					}
					"""),
		FILEWRITER("""
			package test1;

			import java.io.FileWriter;
			import java.io.Writer;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        try {
			            Writer fw=new FileWriter(filename);
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.FileWriter;
					import java.io.OutputStreamWriter;
					import java.io.Writer;
					import java.nio.charset.Charset;
					import java.io.FileNotFoundException;
					import java.io.FileOutputStream;

					public class E1 {
					    void method(String filename) {
					        try {
					            Writer fw=new OutputStreamWriter(new FileOutputStream(filename), Charset.defaultCharset());
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					       }
					    }
					}
					"""),
		INPUTSTREAMREADER("""
			package test1;

			import java.io.InputStreamReader;
			import java.io.FileInputStream;
			import java.io.FileReader;
			import java.io.Reader;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        try {
			            InputStreamReader is=new InputStreamReader(new FileInputStream("")); //$NON-NLS-1$
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.InputStreamReader;
					import java.io.FileInputStream;
					import java.io.FileReader;
					import java.io.Reader;
					import java.nio.charset.Charset;
					import java.io.FileNotFoundException;

					public class E1 {
					    void method(String filename) {
					        try {
					            InputStreamReader is=new InputStreamReader(new FileInputStream(""), Charset.defaultCharset()); //$NON-NLS-1$
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					       }
					    }
					}
					"""),
		OUTPUTSTREAMWRITER("""
			package test1;

			import java.io.ByteArrayOutputStream;
			import java.io.InputStreamReader;
			import java.io.FileInputStream;
			import java.io.FileReader;
			import java.io.Reader;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        try {
			            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream("")); //$NON-NLS-1$
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.ByteArrayOutputStream;
					import java.io.InputStreamReader;
					import java.io.FileInputStream;
					import java.io.FileReader;
					import java.io.Reader;
					import java.nio.charset.Charset;
					import java.io.FileNotFoundException;

					public class E1 {
					    void method(String filename) {
					        try {
					            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(""), Charset.defaultCharset()); //$NON-NLS-1$
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					       }
					    }
					}
					"""),
		PRINTWRITER("""
			package test1;

			import java.io.PrintWriter;
			import java.io.Writer;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        try {
			            Writer w=new PrintWriter(filename);
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.PrintWriter;
					import java.io.Writer;
					import java.nio.charset.Charset;
					import java.io.BufferedWriter;
					import java.io.FileNotFoundException;
					import java.io.FileOutputStream;
					import java.io.OutputStreamWriter;

					public class E1 {
					    void method(String filename) {
					        try {
					            Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), Charset.defaultCharset()));
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					       }
					    }
					}
					"""),
		STRINGGETBYTES("""
			package test1;

			import java.io.ByteArrayOutputStream;
			import java.io.InputStreamReader;
			import java.io.FileInputStream;
			import java.io.FileReader;
			import java.io.Reader;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        String s="asdf"; //$NON-NLS-1$
			        byte[] bytes= s.getBytes();
			        System.out.println(bytes.length);
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.ByteArrayOutputStream;
					import java.io.InputStreamReader;
					import java.io.FileInputStream;
					import java.io.FileReader;
					import java.io.Reader;
					import java.nio.charset.Charset;
					import java.io.FileNotFoundException;

					public class E1 {
					    void method(String filename) {
					        String s="asdf"; //$NON-NLS-1$
					        byte[] bytes= s.getBytes(Charset.defaultCharset());
					        System.out.println(bytes.length);
					       }
					    }
					}
					"""),
		THREE("""
			package test1;

			import java.io.ByteArrayOutputStream;
			import java.io.InputStreamReader;
			import java.io.FileInputStream;
			import java.io.FileReader;
			import java.io.Reader;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        String s="asdf"; //$NON-NLS-1$
			        byte[] bytes= s.getBytes();
			        System.out.println(bytes.length);
			        ByteArrayOutputStream ba=new ByteArrayOutputStream();
			        String result=ba.toString();
			        try {
			            InputStreamReader is=new InputStreamReader(new FileInputStream("")); //$NON-NLS-1$
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			        try {
			            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream("")); //$NON-NLS-1$
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			        try {
			            Reader is=new FileReader(filename);
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.ByteArrayOutputStream;
					import java.io.InputStreamReader;
					import java.io.FileInputStream;
					import java.io.FileReader;
					import java.io.Reader;
					import java.nio.charset.Charset;
					import java.io.FileNotFoundException;

					public class E1 {
					    void method(String filename) {
					        String s="asdf"; //$NON-NLS-1$
					        byte[] bytes= s.getBytes(Charset.defaultCharset());
					        System.out.println(bytes.length);
					        ByteArrayOutputStream ba=new ByteArrayOutputStream();
					        String result=ba.toString(Charset.defaultCharset().displayName());
					        try {
					            InputStreamReader is=new InputStreamReader(new FileInputStream(""), Charset.defaultCharset()); //$NON-NLS-1$
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					        try {
					            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(""), Charset.defaultCharset()); //$NON-NLS-1$
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					        try {
					            Reader is=new InputStreamReader(new FileInputStream(filename), Charset.defaultCharset());
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					       }
					    }
					}
					"""),
					ENCODINGASSTRINGPARAMETER("""
							package test1;

							import java.io.ByteArrayOutputStream;
							import java.io.InputStreamReader;
							import java.io.FileInputStream;
							import java.io.FileReader;
							import java.io.Reader;
							import java.io.FileNotFoundException;

							public class E1 {
							    void method(String filename) {
							        String s="asdf"; //$NON-NLS-1$
							        //byte[] bytes= s.getBytes(StandardCharsets.UTF_8);
							        byte[] bytes= s.getBytes("Utf-8");
							        System.out.println(bytes.length);
							        ByteArrayOutputStream ba=new ByteArrayOutputStream();
							        String result=ba.toString();
							        try {
							            InputStreamReader is=new InputStreamReader(new FileInputStream(""), "UTF-8"); //$NON-NLS-1$
							            } catch (FileNotFoundException e) {
							            e.printStackTrace();
							            }
							        try {
							            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(""), "UTF-8"); //$NON-NLS-1$
							            } catch (FileNotFoundException e) {
							            e.printStackTrace();
							            }
							        try {
							            Reader is=new FileReader(filename, "UTF-8");
							            } catch (FileNotFoundException e) {
							            e.printStackTrace();
							            }
							       }
							    }
							}
							""",

								"""
									package test1;

									import java.io.ByteArrayOutputStream;
									import java.io.InputStreamReader;
									import java.io.FileInputStream;
									import java.io.FileReader;
									import java.io.Reader;
									import java.nio.charset.Charset;
									import java.nio.charset.StandardCharsets;
									import java.io.FileNotFoundException;

									public class E1 {
									    void method(String filename) {
									        String s="asdf"; //$NON-NLS-1$
									        //byte[] bytes= s.getBytes(StandardCharsets.UTF_8);
									        byte[] bytes= s.getBytes("Utf-8");
									        System.out.println(bytes.length);
									        ByteArrayOutputStream ba=new ByteArrayOutputStream();
									        String result=ba.toString(Charset.defaultCharset().displayName());
									        try {
									            InputStreamReader is=new InputStreamReader(new FileInputStream(""), StandardCharsets.UTF_8); //$NON-NLS-1$
									            } catch (FileNotFoundException e) {
									            e.printStackTrace();
									            }
									        try {
									            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(""), StandardCharsets.UTF_8); //$NON-NLS-1$
									            } catch (FileNotFoundException e) {
									            e.printStackTrace();
									            }
									        try {
									            Reader is=new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8);
									            } catch (FileNotFoundException e) {
									            e.printStackTrace();
									            }
									       }
									    }
									}
									""");

		String given;
		String expected;

		ExplicitEncodingPatterns(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}
	}

	@ParameterizedTest
	@EnumSource(ExplicitEncodingPatterns.class)
	public void testExplicitEncodingParametrized(ExplicitEncodingPatterns test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	@Test
	public void testExplicitEncodingdonttouch() throws CoreException{
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E2.java",
				"""
					package test1;

					import java.io.ByteArrayOutputStream;
					import java.io.InputStreamReader;
					import java.io.IOException;
					import java.nio.charset.Charset;
					import java.io.FileInputStream;
					import java.io.FileNotFoundException;
					import java.io.UnsupportedEncodingException;

					public class E2 {
					    void method() throws UnsupportedEncodingException, IOException {
					        String s="asdf"; //$NON-NLS-1$
					        byte[] bytes= s.getBytes(Charset.defaultCharset());
					        System.out.println(bytes.length);
					        ByteArrayOutputStream ba=new ByteArrayOutputStream();
					        String result=ba.toString(Charset.defaultCharset().displayName());
					        try (
					            InputStreamReader is=new InputStreamReader(new FileInputStream(""), Charset.defaultCharset()); //$NON-NLS-1$
					           ){ } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					    }
					}
					""",
				false, null);

		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
